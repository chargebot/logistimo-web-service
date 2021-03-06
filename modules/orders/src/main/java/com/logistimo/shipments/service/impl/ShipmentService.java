/*
 * Copyright © 2018 Logistimo.
 *
 * This file is part of Logistimo.
 *
 * Logistimo software is a mobile & web platform for supply chain management and remote temperature monitoring in
 * low-resource settings, made available under the terms of the GNU Affero General Public License (AGPL).
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. To know more about
 * the commercial license, please contact us at opensource@logistimo.com
 */

package com.logistimo.shipments.service.impl;

import com.logistimo.auth.SecurityConstants;
import com.logistimo.auth.SecurityUtil;
import com.logistimo.auth.utils.SecurityUtils;
import com.logistimo.config.models.DomainConfig;
import com.logistimo.constants.CharacterConstants;
import com.logistimo.constants.Constants;
import com.logistimo.constants.QueryConstants;
import com.logistimo.conversations.entity.IMessage;
import com.logistimo.conversations.service.ConversationService;
import com.logistimo.dao.JDOUtils;
import com.logistimo.domains.utils.DomainsUtil;
import com.logistimo.entities.entity.IKiosk;
import com.logistimo.entities.service.EntitiesService;
import com.logistimo.events.entity.IEvent;
import com.logistimo.exception.InvalidServiceException;
import com.logistimo.exception.LogiException;
import com.logistimo.exception.ValidationException;
import com.logistimo.inventory.entity.IInvAllocation;
import com.logistimo.inventory.entity.IInvntryBatch;
import com.logistimo.inventory.service.InventoryManagementService;
import com.logistimo.logger.XLog;
import com.logistimo.materials.entity.IMaterial;
import com.logistimo.materials.service.MaterialCatalogService;
import com.logistimo.models.ResponseModel;
import com.logistimo.models.shipments.ShipmentItemBatchModel;
import com.logistimo.models.shipments.ShipmentItemModel;
import com.logistimo.models.shipments.ShipmentMaterialsModel;
import com.logistimo.models.shipments.ShipmentModel;
import com.logistimo.orders.actions.GenerateShipmentVoucherAction;
import com.logistimo.orders.entity.IDemandItem;
import com.logistimo.orders.entity.IOrder;
import com.logistimo.orders.models.PDFResponseModel;
import com.logistimo.orders.service.IDemandService;
import com.logistimo.orders.service.OrderManagementService;
import com.logistimo.pagination.Results;
import com.logistimo.proto.JsonTagsZ;
import com.logistimo.security.SecureUserDetails;
import com.logistimo.services.ObjectNotFoundException;
import com.logistimo.services.Resources;
import com.logistimo.services.ServiceException;
import com.logistimo.services.impl.PMF;
import com.logistimo.shipments.FulfilledQuantityModel;
import com.logistimo.shipments.ShipmentRepository;
import com.logistimo.shipments.ShipmentStatus;
import com.logistimo.shipments.ShipmentUtils;
import com.logistimo.shipments.action.ShipmentActivty;
import com.logistimo.shipments.action.UpdateShipmentStatusAction;
import com.logistimo.shipments.entity.IShipment;
import com.logistimo.shipments.entity.IShipmentItem;
import com.logistimo.shipments.entity.IShipmentItemBatch;
import com.logistimo.shipments.service.IShipmentService;
import com.logistimo.shipments.validators.CreateShipmentValidator;
import com.logistimo.users.entity.IUserAccount;
import com.logistimo.users.service.UsersService;
import com.logistimo.utils.BigUtil;
import com.logistimo.utils.LocalDateUtil;
import com.logistimo.utils.LockUtil;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.Transaction;

import static com.logistimo.shipments.ShipmentUtils.extractOrderId;
import static com.logistimo.shipments.ShipmentUtils.generateEvent;

/**
 * Created by Mohan Raja on 29/09/16
 */

@Service
public class ShipmentService implements IShipmentService {

  private static final XLog xLogger = XLog.getLog(ShipmentService.class);
  private SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT);

  private MaterialCatalogService materialCatalogService;
  private InventoryManagementService inventoryManagementService;
  private EntitiesService entitiesService;
  private IDemandService demandService;
  private OrderManagementService orderManagementService;
  private ConversationService conversationService;
  private UsersService usersService;

  private CreateShipmentValidator createShipmentValidator;
  private GenerateShipmentVoucherAction generateShipmentVoucherAction;
  private UpdateShipmentStatusAction updateShipmentStatusAction;

  @Autowired
  public ShipmentService setShipmentActivty(ShipmentActivty shipmentActivty) {
    this.shipmentActivty = shipmentActivty;
    return this;
  }

  private ShipmentActivty shipmentActivty;

  @Autowired
  public ShipmentService setShipmentRepository(ShipmentRepository shipmentRepository) {
    this.shipmentRepository = shipmentRepository;
    return this;
  }

  private ShipmentRepository shipmentRepository;

  @Autowired
  public void setUpdateShipmentStatusAction(UpdateShipmentStatusAction updateShipmentStatusAction){
    this.updateShipmentStatusAction = updateShipmentStatusAction;
  }

  @Autowired
  public void setMaterialCatalogService(MaterialCatalogService materialCatalogService) {
    this.materialCatalogService = materialCatalogService;
  }

  @Autowired
  public void setInventoryManagementService(InventoryManagementService inventoryManagementService) {
    this.inventoryManagementService = inventoryManagementService;
  }

  @Autowired
  public void setEntitiesService(EntitiesService entitiesService) {
    this.entitiesService = entitiesService;
  }

  @Autowired
  public void setDemandService(IDemandService demandService) {
    this.demandService = demandService;
  }

  @Autowired
  public void setOrderManagementService(OrderManagementService orderManagementService) {
    this.orderManagementService = orderManagementService;
  }

  @Autowired
  public void setConversationService(ConversationService conversationService) {
    this.conversationService = conversationService;
  }

  @Autowired
  public void setUsersService(UsersService usersService) {
    this.usersService = usersService;
  }


  @Autowired
  public void setCreateShipmentValidator(CreateShipmentValidator createShipmentValidator) {
    this.createShipmentValidator = createShipmentValidator;
  }

  @Autowired
  public void setGenerateShipmentVoucherAction(
      GenerateShipmentVoucherAction generateShipmentVoucherAction) {
    this.generateShipmentVoucherAction = generateShipmentVoucherAction;
  }

  /**
   * Create shipment
   *
   * @param model {@code ShipmentModel}
   * @return -
   */
  @Override
  @SuppressWarnings("unchecked")
  public String createShipment(ShipmentModel model, int source, Boolean updateOrderFields)
      throws ServiceException, ValidationException {
    LockUtil.LockStatus lockStatus = LockUtil.lock(Constants.TX_O + model.orderId);
    if (!LockUtil.isLocked(lockStatus)) {
      throw new InvalidServiceException(new ServiceException("O002", model.orderId));
    }
    PersistenceManager pm = PMF.get().getPersistenceManager();
    Transaction tx = pm.currentTransaction();
    try {
            /*if (!validate(model)) {
                throw new InvalidDataException("Invalid data. Shipment cannot be created.");
            }*/
      tx.begin();
      Date now = new Date();
      IShipment shipment = JDOUtils.createInstance(IShipment.class);
      shipment.setShipmentId(constructShipmentId(model.orderId));
      shipment.setOrderId(model.orderId);
      shipment.setDomainId(model.sdid);
      shipment.setStatus(model.status != null ? model.status : ShipmentStatus.OPEN);
      Date ead = null;
      if (StringUtils.isNotEmpty(model.ead)) {
        ead = sdf.parse(model.ead);
        shipment.setExpectedArrivalDate(ead);
      }
      shipment.setSalesReferenceId(model.salesRefId);
      shipment.setNumberOfItems(model.items != null ? model.items.size() : 0);
      shipment.setKioskId(model.customerId);
      shipment.setServicingKiosk(model.vendorId);
      shipment.setTransporter(model.transporter);
      shipment.setTrackingId(model.trackingId);
      shipment.setPackageSize(model.ps);
      shipment.setReason(model.reason);
      shipment.setCancelledDiscrepancyReasons(model.cdrsn);
      shipment.setCreatedBy(model.userID);
      shipment.setCreatedOn(now);
      shipment.setLatitude(model.latitude);
      shipment.setLongitude(model.longitude);
      shipment.setGeoAccuracy(model.geoAccuracy);
      shipment.setGeoErrorCode(model.geoError);
      shipment.setSrc(source);
      createShipmentValidator.validate(model);
      DomainsUtil.addToDomain(shipment, model.sdid, null);

      IKiosk vendor = entitiesService.getKiosk(model.vendorId, false);
      DomainConfig dc = DomainConfig.getInstance(vendor.getDomainId());
      boolean containsBatchMaterial = false;
      for (ShipmentItemModel item : model.items) {
        if (item.isBa) {
          containsBatchMaterial = true;
        }
        if (dc.autoGI() && item.afo) {
          BigDecimal transferQuantity = item.q;
          if (item.isBa) {
            List<IInvAllocation> allocations = inventoryManagementService.getAllocationsByTypeId(
                model.vendorId, item.mId,
                IInvAllocation.Type.ORDER, String.valueOf(model.orderId));
            if (allocations != null) {
              item.bq = new ArrayList<>(allocations.size());
              Results<IInvntryBatch>
                  results =
                  inventoryManagementService.getBatches(item.mId, model.vendorId, null);
              if (results != null) {
                List<IInvntryBatch> allBatches = results.getResults();
                boolean complete = false;
                for (IInvntryBatch allBatch : allBatches) {
                  for (IInvAllocation allocation : allocations) {
                    if (allBatch.getBatchId().equals(allocation.getBatchId())) {
                      ShipmentItemBatchModel m = new ShipmentItemBatchModel();
                      m.id = allocation.getBatchId();
                      if (BigUtil.lesserThanEquals(allocation.getQuantity(), transferQuantity)) {
                        m.q = allocation.getQuantity();
                      } else {
                        m.q = transferQuantity;
                      }
                      m.smst = allocation.getMaterialStatus();
                      item.bq.add(m);
                      transferQuantity = transferQuantity.subtract(m.q);
                      if (BigUtil.equalsZero(transferQuantity)) {
                        complete = true;
                        break;
                      }
                    }
                    if (complete) {
                      break;
                    }
                  }
                }
              }
            }
            transferQuantity = null;
          } else {
            List<IInvAllocation>
                allocations =
                inventoryManagementService
                    .getAllocationsByTypeId(model.vendorId, item.mId, IInvAllocation.Type.ORDER,
                        String.valueOf(model.orderId));
            if (!allocations.isEmpty()) {
              if (BigUtil.greaterThan(item.q, allocations.get(0).getQuantity())) {
                transferQuantity = allocations.get(0).getQuantity();
              }
              item.smst = allocations.get(0).getMaterialStatus();
            }
          }
          inventoryManagementService
              .transferAllocation(model.vendorId, item.mId, IInvAllocation.Type.ORDER,
                  String.valueOf(model.orderId),
                  IInvAllocation.Type.SHIPMENT, shipment.getShipmentId(), transferQuantity, item.bq,
                  model.userID, null, pm, item.smst, false);
        }
      }
      pm.makePersistent(shipment);

      final boolean
          isDirectShipOrFulfil =
          model.status != null && !model.status.equals(ShipmentStatus.OPEN);
      String tempSensitiveStatus = null;
      String materialStatus = null;
      if (!containsBatchMaterial && dc.getOrdersConfig().autoAssignFirstMatStatus()) {
        tempSensitiveStatus = dc.getInventoryConfig().getFirstMaterialStatus(true);
        materialStatus = dc.getInventoryConfig().getFirstMaterialStatus(false);
      }
      List<IShipmentItem> items = new ArrayList<>(model.items.size());
      for (ShipmentItemModel item : model.items) {
        item.kid = model.customerId;
        item.uid = model.userID;
        item.sid = shipment.getShipmentId();
        item.sdid = model.sdid;
        setMaterialStatus(model, isDirectShipOrFulfil, tempSensitiveStatus, materialStatus, item);
        IShipmentItem sItem = createShipmentItem(item);
        items.add(sItem);
      }
      pm.makePersistentAll(items);
      items = (List<IShipmentItem>) pm.detachCopyAll(items);
      List<IShipmentItemBatch> bItems = new ArrayList<>(1);
      for (int i = 0; i < model.items.size(); i++) {
        ShipmentItemModel item = model.items.get(i);
        if (item.bq != null) {
          List<IShipmentItemBatch> sbatch = new ArrayList<>(item.bq.size());
          for (ShipmentItemBatchModel quantityByBatch : item.bq) {
            quantityByBatch.uid = model.userID;
            quantityByBatch.mid = item.mId;
            quantityByBatch.kid = model.customerId;
            quantityByBatch.siId = items.get(i).getShipmentItemId();
            quantityByBatch.sdid = model.sdid;
            IShipmentItemBatch sbItem = createShipmentItemBatch(quantityByBatch);
            bItems.add(sbItem);
            sbatch.add(sbItem);
          }
          items.get(i).setShipmentItemBatch(sbatch);
        }
      }
      shipment.setShipmentItems(items);
      if (!bItems.isEmpty()) {
        pm.makePersistentAll(bItems);
      }
      Map<Long, IDemandItem> dItems = demandService.getDemandMetadata(model.orderId, pm);
      for (ShipmentItemModel shipmentItemModel : model.items) {
        IDemandItem demandItem = dItems.get(shipmentItemModel.mId);
        demandItem
            .setInShipmentQuantity(demandItem.getInShipmentQuantity().add(shipmentItemModel.q));
      }
      pm.makePersistentAll(dItems.values());
      // ShipmentStatus is sent as OPEN , since status changes are managed subsequently below.
      shipmentActivty.updateMessageAndActivity(shipment.getShipmentId(), model.comment, model.userID, model.orderId,
          model.sdid, null, ShipmentStatus.OPEN, null, pm);
      // if both conversation and activity returns success, proceed to commit changes
      if (isDirectShipOrFulfil) {
        updateShipmentStatus(shipment.getShipmentId(), model.status, null, model.userID, pm, null,
            shipment, true, false, source, model.salesRefId, ead, updateOrderFields);
      } else {
        orderManagementService.updateOrderMetadata(model.orderId, model.userID, pm);
      }
      generateEvent(model.sdid, IEvent.CREATED, shipment, null, null);

      tx.commit();
      return shipment.getShipmentId();
    } catch (ServiceException | ValidationException ie) {
      throw ie;
    } catch (Exception e) {
      xLogger.severe("Error while creating shipment", e);
      ResourceBundle
          backendMessages =
          Resources.get().getBundle("BackendMessages", SecurityUtils.getLocale());
      throw new ServiceException(backendMessages.getString("shipment.create.error"), e);
    } finally {
      if (tx.isActive()) {
        tx.rollback();
      }
      PMF.close(pm);
      if (LockUtil.shouldReleaseLock(lockStatus) && !LockUtil
          .release(Constants.TX_O + model.orderId)) {
        xLogger.warn("Unable to release lock for key {0}", Constants.TX_O + model.orderId);
      }
    }
  }

  private void setMaterialStatus(ShipmentModel model, boolean isDirectShipOrFulfil,
                                 String tempSensitiveStatus, String materialStatus,
                                 ShipmentItemModel item) throws ServiceException {
    if (isDirectShipOrFulfil && item.bq == null &&
        StringUtils.isEmpty(item.smst) &&
        (StringUtils.isNotBlank(tempSensitiveStatus) || StringUtils.isNotBlank(materialStatus))) {
      IMaterial material = materialCatalogService.getMaterial(item.mId);
      if (material.isTemperatureSensitive()) {
        item.smst = tempSensitiveStatus;
      } else {
        item.smst = materialStatus;
      }
      if (model.status.equals(ShipmentStatus.FULFILLED)) {
        item.fmst = item.smst;
      }
    }
  }

  private IShipmentItemBatch createShipmentItemBatch(ShipmentItemBatchModel batches)
      throws ServiceException {
    Date now = new Date();
    IShipmentItemBatch sbItem = JDOUtils.createInstance(IShipmentItemBatch.class);
    sbItem.setCreatedBy(batches.uid);
    sbItem.setCreatedOn(now);
    sbItem.setUpdatedBy(batches.uid);
    sbItem.setUpdatedOn(now);
    sbItem.setMaterialId(batches.mid);
    sbItem.setKioskId(batches.kid);
    sbItem.setBatchManufacturer(batches.bmfnm);
    sbItem.setShipmentItemId(batches.siId);

    sbItem.setQuantity(batches.q);
    sbItem.setBatchId(batches.id);
    sbItem.setShippedMaterialStatus(batches.smst);
    DomainsUtil.addToDomain(sbItem, batches.sdid, null);
    return sbItem;
  }

  private IShipmentItem createShipmentItem(ShipmentItemModel item) throws ServiceException {
    IShipmentItem sItem = JDOUtils.createInstance(IShipmentItem.class);
    sItem.setCreatedBy(item.uid);
    Date now = new Date();
    sItem.setCreatedOn(now);
    sItem.setUpdatedBy(item.uid);
    sItem.setUpdatedOn(now);
    sItem.setQuantity(item.q);
    sItem.setMaterialId(item.mId);
    sItem.setKioskId(item.kid);
    sItem.setShipmentId(item.sid);
    sItem.setShippedMaterialStatus(item.smst);
    sItem.setFulfilledMaterialStatus(item.fmst);
    DomainsUtil.addToDomain(sItem, item.sdid, null);
    return sItem;
  }

  private boolean validate(ShipmentModel model) throws ParseException {
    Date now = new Date();
    if ((StringUtils.isNotEmpty(model.ead) && sdf.parse(model.ead).before(now))
        || model.items == null) {
      return false;
    }
    return true;
  }

  private String constructShipmentId(Long orderId) throws Exception {
    LockUtil.LockStatus lockStatus = LockUtil.lock(Constants.TX_O + orderId);
    if (!LockUtil.isLocked(lockStatus)) {
      throw new InvalidServiceException(new ServiceException("O002", orderId));
    }
    PersistenceManager pm = PMF.get().getPersistenceManager();
    Query q = null;
    try {
      q = pm.newQuery("javax.jdo.query.SQL", "SELECT count(1) FROM SHIPMENT WHERE ORDERID=?");
      int count = ((Long) ((List) q.executeWithArray(orderId)).iterator().next()).intValue();
      return orderId + CharacterConstants.HYPHEN + (count + 1);
    } finally {
      if (q != null) {
        q.closeAll();
      }
      pm.close();
      if (LockUtil.shouldReleaseLock(lockStatus) && !LockUtil.release(Constants.TX_O + orderId)) {
        xLogger.warn("Unable to release lock for key {0}", Constants.TX_O + orderId);
      }
    }
  }


  @Override
  public ResponseModel updateShipmentStatus(String shipmentId, ShipmentStatus status,
                                            String message,
                                            String userId,
                                            String reason, boolean updateOrderStatus,
                                            PersistenceManager pm, int source)
      throws LogiException {
    return updateShipmentStatus(shipmentId, status, message, userId, pm, reason, null,
        updateOrderStatus, false, source);
  }

  @Override
  public ResponseModel updateShipmentStatus(String shipmentId, ShipmentStatus status,
                                            String message,
                                            String userId,
                                            String reason, int source) throws LogiException {
    return updateShipmentStatus(shipmentId, status, message, userId, null, reason, null, source);
  }

  /**
   * Updtes the shipment's status.
   *
   * @return ResponseModel containing the status (true if success and false if failure) and a
   * message(usually a warning in case of partial success, empty otherwise)
   */
  private ResponseModel updateShipmentStatus(String shipmentId, ShipmentStatus status,
                                             String message,
                                             String userId,
                                             PersistenceManager pm, String reason,
                                             IShipment shipment, int source)
      throws LogiException {
    return updateShipmentStatus(shipmentId, status, message, userId, pm, reason, shipment, true,
        false, source);
  }

  private ResponseModel updateShipmentStatus(String shipmentId, ShipmentStatus status,
                                             String message,
                                             String userId,
                                             PersistenceManager pm, String reason,
                                             IShipment shipment,
                                             boolean isOrderFulfil, int source)
      throws LogiException {
    return updateShipmentStatus(shipmentId, status, message, userId, pm, reason, shipment, true,
        isOrderFulfil, source);
  }

  private ResponseModel updateShipmentStatus(String shipmentId, ShipmentStatus status,
                                             String message, String userId,
                                             PersistenceManager pm, String reason,
                                             IShipment shipment,
                                             boolean updateOrderStatus, boolean isOrderFulfil,
                                             int source) throws LogiException {
    return updateShipmentStatus(shipmentId, status, message, userId, pm, reason, shipment,
        updateOrderStatus, isOrderFulfil, source,
        null, null, false);
  }


  private ResponseModel updateShipmentStatus(String shipmentId, ShipmentStatus status,
                                             String message,
                                             String userId,
                                             PersistenceManager pm, String reason,
                                             IShipment shipment,
                                             boolean updateOrderStatus, boolean isOrderFulfil,
                                             int source, String salesRefId,
                                             Date estimatedDateOfArrival, Boolean updateOrderFields)
      throws LogiException {
    return updateShipmentStatusAction
        .invoke(shipmentId, status, message, userId, pm, reason, shipment, updateOrderStatus, isOrderFulfil, source, salesRefId, estimatedDateOfArrival,
            updateOrderFields);
  }


  /**
   * Update the shipment quantity and allocations with message
   *
   * @param model {@code ShipmentMaterialsModel}
   * @return -
   */
  @Override
  public boolean updateShipment(ShipmentMaterialsModel model) throws LogiException {
    Long orderId = extractOrderId(model.sId);
    LockUtil.LockStatus lockStatus = LockUtil.lock(Constants.TX_O + orderId);
    if (!LockUtil.isLocked(lockStatus)) {
      throw new InvalidServiceException(new ServiceException("O002", orderId));
    }
    IShipment shipment = shipmentRepository.getById(model.sId);
    includeShipmentItems(shipment);
    if (shipment != null) {
      PersistenceManager pm = PMF.get().getPersistenceManager();
      Transaction tx = pm.currentTransaction();
      try {
        IOrder order = orderManagementService.getOrder(orderId);
        if (StringUtils.isNotBlank(model.orderUpdatedAt)) {
          IUserAccount userAccount = usersService.getUserAccount(order.getUpdatedBy());
          if (!model.orderUpdatedAt.equals(
              LocalDateUtil.formatCustom(order.getUpdatedOn(), Constants.DATETIME_FORMAT, null))) {
            throw new LogiException("O004", userAccount.getFullName(),
                LocalDateUtil.format(order.getUpdatedOn(), SecurityUtils.getLocale(),
                    userAccount.getTimezone()));
          }
        }
        List<IShipmentItemBatch> newShipmentItemBatches = new ArrayList<>();
        List<IShipmentItemBatch> delShipmentItemBatches = new ArrayList<>();
        for (ShipmentItemModel item : model.items) {
          for (IShipmentItem shipmentItem : shipment.getShipmentItems()) {
            if (item.mId.equals(shipmentItem.getMaterialId())) {
              if (item.isBa) {
                item.aq = null;
                for (ShipmentItemBatchModel shipmentItemBatchModel : item.bq) {
                  boolean newBatch = true;
                  int ind = 0;
                  for (IShipmentItemBatch shipmentItemBatch : shipmentItem.getShipmentItemBatch()) {
                    if (shipmentItemBatch.getBatchId().equals(shipmentItemBatchModel.id)) {
                      if (shipmentItemBatchModel.q == null || BigUtil
                          .equalsZero(shipmentItemBatchModel.q)) {
                        delShipmentItemBatches.add(shipmentItem.getShipmentItemBatch().get(ind));
                        item.smst = null;
                      } else {
                        shipmentItemBatch.setQuantity(shipmentItemBatchModel.q);
                        shipmentItemBatch.setShippedMaterialStatus(shipmentItemBatchModel.smst);
                      }
                      newBatch = false;
                      break;
                    }
                    ind++;
                  }
                  if (newBatch && BigUtil.greaterThanZero(shipmentItemBatchModel.q)) {
                    shipmentItemBatchModel.uid = model.userId;
                    shipmentItemBatchModel.kid = shipmentItem.getKioskId();
                    shipmentItemBatchModel.mid = shipmentItem.getMaterialId();
                    shipmentItemBatchModel.siId = shipmentItem.getShipmentItemId();
                    shipmentItemBatchModel.sdid = shipmentItem.getDomainId();
                    IShipmentItemBatch b = createShipmentItemBatch(shipmentItemBatchModel);
                    newShipmentItemBatches.add(b);
                  }
                }
              } else {
                shipmentItem.setQuantity(item.q);
                if (item.aq == null) {
                  item.smst = null;
                }
                shipmentItem.setShippedMaterialStatus(item.smst);
              }
              break;
            }
          }
        }
        tx.begin();
        pm.makePersistentAll(newShipmentItemBatches);
        for (IShipmentItem shipmentItem : shipment.getShipmentItems()) {
          if (shipmentItem.getShipmentItemBatch() != null) {
            pm.makePersistentAll(shipmentItem.getShipmentItemBatch());
          }
        }
        if (CollectionUtils.isNotEmpty(delShipmentItemBatches)) {
          pm.deletePersistentAll(delShipmentItemBatches);
        }
        pm.makePersistentAll(shipment.getShipmentItems());
        pm.makePersistentAll(newShipmentItemBatches);
        for (ShipmentItemModel item : model.items) {
          //Remove all emptied batches or make allocation zero.
          if (item.isBa) {
            for (ShipmentItemBatchModel itemBatch : item.bq) {
              if (itemBatch.q == null || BigUtil.equalsZero(itemBatch.q)) {
                inventoryManagementService
                    .clearBatchAllocation(model.kid, item.mId, IInvAllocation.Type.SHIPMENT,
                        model.sId,
                        itemBatch.id, pm);
              }

            }
          } else if (item.aq == null || BigUtil.equalsZero(item.aq)) {
            inventoryManagementService
                .clearAllocation(model.kid, item.mId, IInvAllocation.Type.SHIPMENT, model.sId, pm);
          }
          //allocate remaining from Order.
          String tag = IInvAllocation.Type.ORDER + CharacterConstants.COLON + orderId;
          if (item.aq != null && BigUtil.greaterThan(item.aq, item.q)) {
            ResourceBundle
                backendMessages =
                Resources.get().getBundle("BackendMessages", SecurityUtils.getLocale());
            throw new ServiceException(backendMessages.getString("allocated.qty.greater"));
          }
          inventoryManagementService
              .transferAllocation(model.kid, item.mId, IInvAllocation.Type.ORDER,
                  orderId.toString(),
                  IInvAllocation.Type.SHIPMENT, model.sId, item.aq, item.bq, model.userId, tag, pm,
                  item.smst, true);
        }
        shipment.setUpdatedOn(new Date());
        shipment.setUpdatedBy(model.userId);
        pm.makePersistent(shipment);
        orderManagementService.updateOrderMetadata(orderId, model.userId, pm);
        generateEvent(shipment.getDomainId(), IEvent.MODIFIED, shipment, null, null);
        tx.commit();
      } catch (ObjectNotFoundException e) {
        xLogger.warn("Order not found - ", e);
      } catch (LogiException le) {
        xLogger.warn("Error while updating shipment", le);
        throw le;
      } finally {
        if (tx.isActive()) {
          tx.rollback();
        }
        pm.close();
        if (LockUtil.shouldReleaseLock(lockStatus) && !LockUtil.release(Constants.TX_O + orderId)) {
          xLogger.warn("Unable to release lock for key {0}", Constants.TX_O + orderId);
        }
      }
    }
    return true;
  }

  public IShipment updateShipmentData(Map<String, String> shipmentMetadata, String orderUpdatedAt,
                                      String sId, String userId)
      throws LogiException {
    if (shipmentMetadata == null || shipmentMetadata.isEmpty()) {
      throw new IllegalArgumentException("No meta data provided for updating the shipment");
    }
    Long orderId = extractOrderId(sId);
    LockUtil.LockStatus lockStatus = LockUtil.lock(Constants.TX_O + orderId);
    if (!LockUtil.isLocked(lockStatus)) {
      throw new InvalidServiceException(new ServiceException("O002", orderId));
    }
    IShipment shipment = shipmentRepository.getById(sId);
    if (shipment != null) {
      PersistenceManager pm = PMF.get().getPersistenceManager();
      Transaction tx = pm.currentTransaction();
      try {
        IOrder order = orderManagementService.getOrder(orderId);
        if (StringUtils.isNotBlank(orderUpdatedAt)) {
          if (!orderUpdatedAt.equals(
              LocalDateUtil.formatCustom(order.getUpdatedOn(), Constants.DATETIME_FORMAT, null))) {
            IUserAccount userAccount = usersService.getUserAccount(order.getUpdatedBy());
            throw new LogiException("O004", userAccount.getFullName(),
                LocalDateUtil.format(order.getUpdatedOn(), SecurityUtils.getLocale(),
                    userAccount.getTimezone()));
          }
        }
        shipmentMetadata.entrySet().stream().forEach(entry -> {
          if ("tpName".equals(entry.getKey())) {
            shipment.setTransporter(entry.getValue());
          } else if ("tId".equals(entry.getKey())) {
            shipment.setTrackingId(entry.getValue());
          } else if ("rsn".equals(entry.getKey())) {
            shipment.setReason(entry.getValue());//todo: update reason for shipment pending.
          } else if ("date".equals(entry.getKey())) {
            if (StringUtils.isNotBlank(entry.getValue())) {
              try {
                shipment.setExpectedArrivalDate(sdf.parse(entry.getValue()));
              } catch (ParseException e) {
                throw new IllegalArgumentException(e);
              }
            } else {
              shipment.setExpectedArrivalDate(null);
            }
          } else if ("ps".equals(entry.getKey())) {
            shipment.setPackageSize(entry.getValue());
          } else if ("rid".equals(entry.getKey())) {
            shipment.setSalesReferenceId(entry.getValue());
          }
        });

        shipment.setUpdatedBy(userId);
        shipment.setUpdatedOn(new Date());

        generateEvent(shipment.getDomainId(), IEvent.MODIFIED, shipment, null, null);

        tx.begin();
        pm.makePersistent(shipment);
        orderManagementService.updateOrderMetadata(orderId, userId, pm);
        tx.commit();
        return shipmentRepository.getById(sId);
      } catch (InvalidServiceException | IllegalArgumentException e) {
        xLogger.warn("Error while updating shipment", e);
        throw e;
      } catch (LogiException le) {
        throw le;
      } catch (Exception e) {
        xLogger.warn("Error while updating shipment", e);
        throw new ServiceException(e);
      } finally {
        if (tx.isActive()) {
          tx.rollback();
        }
        pm.close();
        if (LockUtil.shouldReleaseLock(lockStatus) && !LockUtil.release(Constants.TX_O + orderId)) {
          xLogger.warn("Unable to release lock for key {0}", Constants.TX_O + orderId);
        }
      }
    }

    throw new ServiceException("Shipment not found");
  }

  @Override
  public ResponseModel fulfillShipment(String shipmentId, String userId, int source)
      throws ServiceException {
    IShipment shipment = shipmentRepository.getById(shipmentId);
    includeShipmentItems(shipment);
    ShipmentMaterialsModel sModel = new ShipmentMaterialsModel();
    sModel.sId = shipmentId;
    sModel.userId = userId;
    sModel.afd = sdf.format(new Date());
    sModel.items = new ArrayList<>();
    for (IShipmentItem sItem : shipment.getShipmentItems()) {
      ShipmentItemModel siModel = new ShipmentItemModel();
      siModel.mId = sItem.getMaterialId();
      siModel.fq = siModel.q = sItem.getQuantity();
      IMaterial material = materialCatalogService.getMaterial(sItem.getMaterialId());
      IKiosk kiosk = entitiesService.getKiosk(shipment.getServicingKiosk(), false);
      siModel.isBa = material.isBatchEnabled() && kiosk.isBatchMgmtEnabled();
      if (siModel.isBa) {
        siModel.bq = new ArrayList<>();

        if (ShipmentStatus.OPEN.equals(shipment.getStatus())) {
          List<IInvAllocation> allocations =
              inventoryManagementService.getAllocationsByTypeId(shipment.getServicingKiosk(),
                  siModel.mId, IInvAllocation.Type.SHIPMENT, shipmentId);
          if (allocations != null && !allocations.isEmpty()) {
            for (IInvAllocation alloc : allocations) {
              if (BigUtil.greaterThanZero(alloc.getQuantity())) {
                ShipmentItemBatchModel sbModel = new ShipmentItemBatchModel();
                sbModel.fq = sbModel.q = alloc.getQuantity();
                sbModel.id = alloc.getBatchId();
                siModel.bq.add(sbModel);
              }
            }
          }
        } else {
          for (IShipmentItemBatch shipmentItemBatch : sItem.getShipmentItemBatch()) {
            ShipmentItemBatchModel sbModel = new ShipmentItemBatchModel();
            sbModel.fq = sbModel.q = shipmentItemBatch.getQuantity();
            sbModel.id = shipmentItemBatch.getBatchId();
            siModel.bq.add(sbModel);
          }
        }
      }
      sModel.items.add(siModel);
    }

    return fulfillShipment(sModel, userId, source);
  }


  @Override
  public ResponseModel fulfillShipment(ShipmentMaterialsModel model, String userId, int source) {
    Long orderId = extractOrderId(model.sId);
    LockUtil.LockStatus lockStatus = LockUtil.lock(Constants.TX_O + orderId);
    if (!LockUtil.isLocked(lockStatus)) {
      throw new InvalidServiceException(new ServiceException("O002", orderId));
    }
    PersistenceManager pm = null;
    Transaction tx = null;
    ResponseModel responseModel = new ResponseModel();
    try {
      IOrder order = orderManagementService.getOrder(orderId);
      if (StringUtils.isNotBlank(model.orderUpdatedAt)) {
        if (!model.orderUpdatedAt.equals(
            LocalDateUtil.formatCustom(order.getUpdatedOn(), Constants.DATETIME_FORMAT, null))) {
          IUserAccount userAccount = usersService.getUserAccount(order.getUpdatedBy());
          throw new LogiException("O004", userAccount.getFullName(),
              LocalDateUtil.format(order.getUpdatedOn(), SecurityUtils.getLocale(),
                  userAccount.getTimezone()));
        }
      }
      pm = PMF.get().getPersistenceManager();
      tx = pm.currentTransaction();
      tx.begin();
      IShipment shipment = shipmentRepository.getById(model.sId, true, pm);
      Map<Long, List<IShipmentItemBatch>> newShipmentItemBatchesMap = new HashMap<>();
      if (shipment != null) {
        for (ShipmentItemModel item : model.items) {
          for (IShipmentItem shipmentItem : shipment.getShipmentItems()) {
            if (item.mId.equals(shipmentItem.getMaterialId())) {
              if (item.isBa) {
                if (item.bq == null || item.bq.isEmpty()) {
                  ResourceBundle
                      backendMessages =
                      Resources.get().getBundle("BackendMessages", SecurityUtils.getLocale());
                  throw new IllegalArgumentException(
                      backendMessages.getString("shipment.fulfill.error") + " " + model.sId +
                          ", " + backendMessages.getString("materials.batch.empty") + " "
                          + item.mnm);
                }
                BigDecimal totalFQ = BigDecimal.ZERO;
                BigDecimal totalDQ = BigDecimal.ZERO;
                for (ShipmentItemBatchModel shipmentItemBatchModel : item.bq) {
                  boolean newBatch = true;
                  for (IShipmentItemBatch shipmentItemBatch : shipmentItem.getShipmentItemBatch()) {
                    if (shipmentItemBatch.getBatchId().equals(shipmentItemBatchModel.id)) {
                      if (shipmentItemBatchModel.fq == null || BigUtil.lesserThanZero(
                          shipmentItemBatchModel.fq)) {
                        ResourceBundle
                            backendMessages =
                            Resources.get().getBundle("BackendMessages", SecurityUtils.getLocale());
                        throw new ServiceException(
                            backendMessages.getString("shipment.batch.item") + " " +
                                shipmentItemBatchModel.id + " " + backendMessages
                                .getString("qty.lower") + " " +
                                shipmentItemBatchModel.fq + backendMessages.getString("valid.qty"));
                      }
                      updateShipmentItemBatch(shipmentItemBatch, shipmentItemBatchModel,
                          Boolean.FALSE);
                      //shipmentItemBatch.setDiscrepancyQuantity(shipmentItemBatch.getQuantity().subtract(shipmentItemBatchModel.q));
                      totalFQ = totalFQ.add(shipmentItemBatch.getFulfilledQuantity());
                      totalDQ = totalDQ.add(shipmentItemBatch.getDiscrepancyQuantity());
                      newBatch = false;
                      break;
                    }
                  }
                  if (newBatch && BigUtil.greaterThanZero(shipmentItemBatchModel.fq)) {
                    IShipmentItemBatch shipmentItemBatch =
                        getShipmentItemBatchFromShipmentItem(model.userId, shipmentItem,
                            shipmentItemBatchModel);
                    shipmentItemBatch.setBatchExpiry(LocalDateUtil
                        .parseCustom(shipmentItemBatchModel.e, Constants.DATE_FORMAT, null));
                    if (StringUtils.isNotEmpty(shipmentItemBatchModel.bmfdt)) {
                      shipmentItemBatch.setBatchManufacturedDate(LocalDateUtil
                          .parseCustom(shipmentItemBatchModel.bmfdt, Constants.DATE_FORMAT, null));
                    }
                    updateShipmentItemBatch(shipmentItemBatch, shipmentItemBatchModel,
                        Boolean.FALSE);
                    totalFQ = totalFQ.add(shipmentItemBatch.getFulfilledQuantity());
                    totalDQ = totalDQ.add(shipmentItemBatch.getDiscrepancyQuantity());

                    if (newShipmentItemBatchesMap.containsKey(shipmentItem.getMaterialId())) {
                      newShipmentItemBatchesMap.get(shipmentItem.getMaterialId())
                          .add(shipmentItemBatch);
                    } else {
                      List<IShipmentItemBatch> newShipmentItemBatches = new ArrayList<>(1);
                      newShipmentItemBatches.add(shipmentItemBatch);
                      newShipmentItemBatchesMap
                          .put(shipmentItem.getMaterialId(), newShipmentItemBatches);
                    }
                  }
                }
                shipmentItem.setFulfilledQuantity(totalFQ);
                shipmentItem.setDiscrepancyQuantity(totalDQ);
              } else {
                shipmentItem.setFulfilledQuantity(item.fq);
                shipmentItem.setDiscrepancyQuantity(shipmentItem.getQuantity().subtract(item.fq));
                shipmentItem.setFulfilledMaterialStatus(item.fmst);
                shipmentItem.setFulfilledDiscrepancyReason(item.frsn);
              }
              break;
            }
          }
        }

        for (IShipmentItem shipmentItem : shipment.getShipmentItems()) {
          List<IShipmentItemBatch> shipmentItemBatches = (List<IShipmentItemBatch>) shipmentItem
              .getShipmentItemBatch();
          if (newShipmentItemBatchesMap.containsKey(shipmentItem.getMaterialId())) {
            shipmentItemBatches.addAll(newShipmentItemBatchesMap.get(shipmentItem.getMaterialId()));
          }
        }

        for (IShipmentItem shipmentItem : shipment.getShipmentItems()) {
          List<IShipmentItemBatch> batches = (List<IShipmentItemBatch>) shipmentItem
              .getShipmentItemBatch();
          if (CollectionUtils.isNotEmpty(batches)) {
            pm.makePersistentAll(batches);
          }
        }
        pm.makePersistentAll(shipment.getShipmentItems());
        Map<Long, IDemandItem> items = demandService.getDemandMetadata(extractOrderId(model.sId), pm);
        for (IShipmentItem sItem : shipment.getShipmentItems()) {
          IDemandItem item = items.get(sItem.getMaterialId());
          item.setFulfilledQuantity(item.getFulfilledQuantity().add(sItem.getFulfilledQuantity()));
          item.setDiscrepancyQuantity(
              item.getDiscrepancyQuantity().add(sItem.getDiscrepancyQuantity()));
        }
        pm.makePersistentAll(items.values());
        shipment.setActualFulfilmentDate(sdf.parse(model.afd));
        pm.makePersistent(shipment);
        responseModel =
            updateShipmentStatus(model.sId, ShipmentStatus.FULFILLED, model.msg, model.userId,
                pm, null, shipment, model.isOrderFulfil, source);
        orderManagementService.updateOrderMetadata(orderId, userId, pm);
        tx.commit();
      }
    } catch (InvalidServiceException | LogiException e) {
      xLogger.warn("Error while updating shipment", e);
      throw new InvalidServiceException(e.getMessage());
    } catch (Exception e1) {
      xLogger.warn("Error while updating shipment", e1);
      throw new InvalidServiceException(e1);
    } finally {
      if (tx != null && tx.isActive()) {
        tx.rollback();
      }
      if (pm != null) {
        pm.close();
      }
      if (LockUtil.shouldReleaseLock(lockStatus) && !LockUtil.release(Constants.TX_O + orderId)) {
        xLogger.warn("Unable to release lock for key {0}", Constants.TX_O + orderId);
      }
    }

    return responseModel;
  }


  private IShipmentItemBatch getShipmentItemBatchFromShipmentItem(String userId,
                                                                  IShipmentItem shipmentItem,
                                                                  ShipmentItemBatchModel shipmentItemBatchModel)
      throws ServiceException {
    shipmentItemBatchModel.uid = userId;
    shipmentItemBatchModel.kid = shipmentItem.getKioskId();
    shipmentItemBatchModel.mid = shipmentItem.getMaterialId();
    shipmentItemBatchModel.siId = shipmentItem.getShipmentItemId();
    shipmentItemBatchModel.sdid = shipmentItem.getDomainId();
    return createShipmentItemBatch(shipmentItemBatchModel);
  }

  private void updateShipmentItemBatch(IShipmentItemBatch shipmentItemBatch,
                                       ShipmentItemBatchModel shipmentItemBatchModel,
                                       Boolean setBatch) throws ParseException {

    shipmentItemBatch.setFulfilledMaterialStatus(shipmentItemBatchModel.fmst);
    shipmentItemBatch.setFulfilledQuantity(shipmentItemBatchModel.fq);
    shipmentItemBatch.setDiscrepancyQuantity(
        shipmentItemBatch.getQuantity().subtract(shipmentItemBatchModel.fq));
    shipmentItemBatch.setFulfilledDiscrepancyReason(shipmentItemBatchModel.frsn);
    if (setBatch) {
      shipmentItemBatch.setBatchExpiry(
          LocalDateUtil.parseCustom(shipmentItemBatchModel.e, Constants.DATE_FORMAT, null));
      shipmentItemBatch.setBatchId(shipmentItemBatchModel.id);
      shipmentItemBatch.setBatchManufacturer(shipmentItemBatchModel.bmfnm);
      shipmentItemBatch.setBatchManufacturedDate(
          LocalDateUtil.parseCustom(shipmentItemBatchModel.bmfdt, Constants.DATE_FORMAT, null));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Results getShipments(String userId, Long domainId, Long custId, Long vendId, Date from,
                              Date to,
                              Date etaFrom, Date etaTo, String transporter, String trackingId,
                              ShipmentStatus status, int size, int offset) {
    if (domainId == null) {
      xLogger.warn("Domain id is required while getting the shipments");
      return null;
    }

    PersistenceManager pm = PMF.get().getPersistenceManager();
    Query query = null;
    Query cntQuery = null;
    try {
      List<String> params = new ArrayList<>(1);
      StringBuilder queryStr = new StringBuilder();
      queryStr.append(
          "SELECT * FROM SHIPMENT S, SHIPMENT_DOMAINS SD WHERE SD.ID_OID = S.ID AND SD.DOMAIN_ID = ?");
      params.add(String.valueOf(domainId));
      if (custId != null) {
        queryStr.append(" AND S.KID=?");
        params.add(String.valueOf(custId));

        IUserAccount acc = usersService.getUserAccount(userId);
        if (SecurityUtil.compareRoles(acc.getRole(), SecurityConstants.ROLE_DOMAINOWNER) < 0) {
          queryStr.append(
              " AND ( S.STATUS IN ('sp','fl') OR EXISTS(SELECT 1 FROM USERTOKIOSK WHERE USERID = ? AND KIOSKID = S.SKID)"
                  +
                  " OR EXISTS (SELECT 1 FROM KIOSKLINK KL, KIOSK K WHERE KL.KIOSKID IN (SELECT UK.KIOSKID FROM USERTOKIOSK UK "
                  +
                  "WHERE USERID = ?) AND ((KL.LINKTYPE = 'c' AND K.CPERM > 0) " +
                  " OR (KL.LINKTYPE = 'v' AND K.VPERM > 0))" +
                  " AND K.KIOSKID = KL.KIOSKID AND S.SKID = KL.LINKEDKIOSKID LIMIT 1))");
          params.add(userId);
          params.add(userId);
        }
      }
      if (vendId != null) {
        queryStr.append(" AND S.SKID=?");
        params.add(String.valueOf(vendId));
      }
      if (from != null) {
        queryStr.append(" AND S.CON >=?");
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT_CSV);
        params.add(sdf.format(from));
      }
      if (to != null) {
        queryStr.append(" AND S.CON <?");
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT_CSV);
        params.add(sdf.format(to));
      }
      if (etaFrom != null) {
        queryStr.append(" AND S.AFD >=?");
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT_CSV);
        params.add(sdf.format(etaFrom));
      }
      if (etaTo != null) {
        queryStr.append(" AND S.AFD <?");
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT_CSV);
        params.add(sdf.format(etaTo));
      }
      if (StringUtils.isNotEmpty(transporter)) {
        queryStr.append(" AND S.TRANSPORTER=?");
        params.add(transporter);
      }
      if (StringUtils.isNotEmpty(trackingId)) {
        queryStr.append(" AND S.TRACKINGID=?");
        params.add(trackingId);
      }
      if (status != null) {
        queryStr.append(" AND S.STATUS=?");
        params.add(status.toString());
      }
      queryStr.append(" ORDER BY S.CON DESC");
      String limitStr = " LIMIT " + offset + CharacterConstants.COMMA + size;
      queryStr.append(limitStr);
      query = pm.newQuery("javax.jdo.query.SQL", queryStr.toString());
      query.setClass(JDOUtils.getImplClass(IShipment.class));
      List list = (List) query.executeWithArray(params.toArray());
      List<IShipment> shipments = new ArrayList<>(list.size());
      for (Object o : list) {
        shipments.add((IShipment) o);
      }
      String cntQueryStr = queryStr.toString().replace("*", QueryConstants.ROW_COUNT);
      cntQueryStr = cntQueryStr.replace(limitStr, CharacterConstants.EMPTY);
      cntQuery = pm.newQuery("javax.jdo.query.SQL", cntQueryStr);
      int
          count =
          ((Long) ((List) cntQuery.executeWithArray(params.toArray())).iterator().next())
              .intValue();
      return new Results(shipments, null, count, offset);
    } catch (Exception e) {
      xLogger.severe("Error while getting the shipments", e);
    } finally {
      if (query != null) {
        query.closeAll();
      }
      if (cntQuery != null) {
        cntQuery.closeAll();
      }
      pm.close();
    }
    return null;
  }

  @Override
  public IShipment getShipment(String shipId) {
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
      return shipmentRepository.getById(shipId, true, pm);
    } catch (JDOObjectNotFoundException e) {
      throw new ObjectNotFoundException("Shipment not found : " + shipId);
    } finally {
      pm.close();
    }
  }


  @Override
  public List<IShipment> getShipmentsByOrderId(Long orderId) {
    PersistenceManager pm = null;
    try {
      pm = PMF.get().getPersistenceManager();
      return getShipmentsByOrderId(orderId, pm);
    } finally {
      if (pm != null) {
        pm.close();
      }
    }
  }

  /**
   * Get all shipment for a specific order
   *
   * @param orderId Order Id
   * @return -
   */
  @Override
  @SuppressWarnings("unchecked")
  public List<IShipment> getShipmentsByOrderId(Long orderId, PersistenceManager pm) {
    return shipmentRepository.getByOrderId(orderId, pm);
  }



  @Override
  public void includeShipmentItems(IShipment shipment) {
    PersistenceManager pm = null;
    try {
      pm = PMF.get().getPersistenceManager();
      ShipmentUtils.includeShipmentItems(shipment, pm);
    } finally {
      if (pm != null) {
        pm.close();
      }
    }
  }

  @SuppressWarnings("unchecked")

  @Override
  public List<String> getTransporterSuggestions(Long domainId, String text) {
    List<String> filterIds = new ArrayList<>();
    List<Object> parameters = new ArrayList<>();
    PersistenceManager pm = PMF.get().getPersistenceManager();
    Query query = pm.newQuery("javax.jdo.query.SQL",
            "SELECT DISTINCT TRANSPORTER FROM `SHIPMENT` WHERE ID IN "
                + "(SELECT ID_OID FROM SHIPMENT_DOMAINS WHERE DOMAIN_ID = ?) AND "
                + "TRANSPORTER LIKE ? LIMIT 0,8");
    parameters.add(domainId);
    parameters.add(text.concat("%"));
    try {
      List rs = (List) query.executeWithArray(parameters.toArray());
      for (Object r : rs) {
        String a = String.valueOf(r);
        if (a != null) {
          filterIds.add(a);
        }
      }
    } catch (Exception e) {
      xLogger.warn("Error in fetching id suggestions for domain:{0}", domainId, e);
    } finally {
      query.closeAll();
      pm.close();
    }
    return filterIds;
  }

  @Override
  public IMessage addMessage(String shipmentId, String message, String userId)
      throws ServiceException {
    IShipment shipment = shipmentRepository.getById(shipmentId);
    IMessage
        iMessage =
        conversationService.addMsgToConversation("SHIPMENT", shipmentId, message, userId,
            Collections.singleton("SHIPMENT:" + shipmentId),
            shipment.getDomainId(), null);
    orderManagementService.generateOrderCommentEvent(shipment.getDomainId(), IEvent.COMMENTED,
        JDOUtils.getImplClassName(IShipment.class), shipmentId, null, null);
    return iMessage;
  }


  public BigDecimal getAllocatedQuantityForShipmentItem(String sId, Long kId, Long mId) {
    try {
      List<IInvAllocation>
          iAllocs =
          inventoryManagementService
              .getAllocationsByTypeId(kId, mId, IInvAllocation.Type.SHIPMENT, sId);
      if (iAllocs != null && !iAllocs.isEmpty()) {
        BigDecimal alq = BigDecimal.ZERO;
        for (IInvAllocation iAlloc : iAllocs) {
          alq = alq.add(iAlloc.getQuantity());
        }
        return alq;
      }
    } catch (Exception e) {
      xLogger.warn("Exception while getting inventory allocation for the shipment {0}", sId, e);
    }
    return null;
  }

  private BigDecimal getAllocatedQuantityForShipmentItem(InventoryManagementService ims,
                                                         IShipmentItem shipmentItem) {

    try {
      List<IInvAllocation>
          iAllocs =
          ims.getAllocationsByTypeId(shipmentItem.getKioskId(), shipmentItem.getMaterialId(),
              IInvAllocation.Type.SHIPMENT, String.valueOf(shipmentItem.getShipmentItemId()));
      if (iAllocs != null && !iAllocs.isEmpty()) {
        BigDecimal alq = new BigDecimal(0);
        for (IInvAllocation iAlloc : iAllocs) {
          alq = alq.add(iAlloc.getQuantity());
        }
        return alq;
      }
    } catch (Exception e) {
      xLogger.warn("Exception while getting inventory allocation for the demand item {0}",
          shipmentItem.getShipmentId(), e);
    }
    return null;
  }

  public Map<String, String> getShipmentItemBatchAsMap(IShipmentItemBatch sib, String timezone) {
    Map<String, String> map = new HashMap<>(1);
    if (StringUtils.isNotEmpty(sib.getBatchId())) {
      map.put(JsonTagsZ.BATCH_ID, sib.getBatchId());
    }
    if (StringUtils.isNotEmpty(sib.getBatchManufacturer())) {
      map.put(JsonTagsZ.BATCH_MANUFACTUER_NAME, sib.getBatchManufacturer());
    }
    if (sib.getBatchManufacturedDate() != null) {
      map.put(JsonTagsZ.BATCH_MANUFACTURED_DATE,
          LocalDateUtil.formatCustom(sib.getBatchManufacturedDate(), Constants.DATE_FORMAT,
              timezone));
    }
    if (sib.getBatchExpiry() != null) {
      map.put(JsonTagsZ.BATCH_EXPIRY,
          LocalDateUtil.formatCustom(sib.getBatchExpiry(), Constants.DATE_FORMAT, timezone));
    }

    map.put(JsonTagsZ.QUANTITY, BigUtil.getFormattedValue(sib.getQuantity()));
    map.put(JsonTagsZ.FULFILLED_QUANTITY, BigUtil.getFormattedValue(sib.getFulfilledQuantity()));
    if (sib.getUpdatedOn() != null) {
      map.put(JsonTagsZ.TIMESTAMP,
          LocalDateUtil.formatCustom(sib.getUpdatedOn(), Constants.DATE_FORMAT, timezone));
    } else if (sib.getCreatedOn() != null) {
      map.put(JsonTagsZ.TIMESTAMP,
          LocalDateUtil.formatCustom(sib.getCreatedOn(), Constants.DATE_FORMAT, timezone));
    }
    // Get inventoryAllocation for batch
    try {
      List<IInvAllocation>
          iAllocs =
          inventoryManagementService.getAllocationsByTypeId(sib.getKioskId(), sib.getMaterialId(),
              IInvAllocation.Type.SHIPMENT, sib.getShipmentItemId().toString());

      if (iAllocs != null && !iAllocs.isEmpty()) {
        for (IInvAllocation iAlloc : iAllocs) {
          if (iAlloc.getBatchId() != null && !iAlloc.getBatchId().isEmpty() && iAlloc.getBatchId()
              .equals(sib.getBatchId())) {
            map.put(JsonTagsZ.ALLOCATED_QUANTITY, BigUtil.getFormattedValue(iAlloc.getQuantity()));
          }
        }
      }
    } catch (Exception e) {
      xLogger.warn(
          "Exception while getting inventory allocation for the shipment item batch with id {0}",
          sib.getShipmentItemId(), e);
    }
    return map;
  }


  @Override
  public PDFResponseModel generateShipmentVoucher(String shipmentId)
      throws ServiceException, ObjectNotFoundException, IOException, ValidationException {
    IOrder order = orderManagementService.getOrder(extractOrderId(shipmentId), true);
    IShipment shipment = getShipment(shipmentId);
    includeShipmentItems(shipment);
    SecureUserDetails user = SecurityUtils.getUserDetails();
    return generateShipmentVoucherAction.invoke(order, shipment, user);
  }

  public List<FulfilledQuantityModel> getFulfilledQuantityByOrderId(Long orderId,
                                                                    List<Long> materialIdList)
      throws ServiceException {
    if (orderId == null) {
      return Collections.emptyList();
    }
    List<FulfilledQuantityModel> fulfilledQuantityModelList = new ArrayList<>();
    List<Object> parameters = new ArrayList<>();
    PersistenceManager pm = PMF.get().getPersistenceManager();
    try {
//      Query q =
//          pm.newQuery("javax.jdo.query.SQL",
//              "SELECT SI.MID, BID, SUM(SI.FQ),SUM(SIB.FQ), SI.SID FROM "
//                  + "SHIPMENT S,SHIPMENTITEM SI LEFT JOIN  SHIPMENTITEMBATCH SIB ON SIB.SIID = SI.ID "
//                  + "WHERE S.ORDERID=? AND S.ID = SI.SID AND SI.MID IN (?) GROUP BY SI.MID,BID");

      StringBuilder
          queryBuilder =
          new StringBuilder("SELECT SI.MID, BID, SUM(SI.FQ),SUM(SIB.FQ), SI.SID,SIB.BEXP,SIB.BMFDT,SIB.BMFNM  FROM "
              + "SHIPMENT S,SHIPMENTITEM SI LEFT JOIN  SHIPMENTITEMBATCH SIB ON SIB.SIID = SI.ID "
              + "WHERE S.ORDERID=? AND S.ID = SI.SID ");
      parameters.add(orderId);

      if(CollectionUtils.isNotEmpty(materialIdList)) {
        queryBuilder.append(" AND SI.MID IN (");
        for (Long matId : materialIdList) {
          queryBuilder.append(CharacterConstants.QUESTION).append(CharacterConstants.COMMA);
          parameters.add(matId);
        }
        queryBuilder.setLength(queryBuilder.length() - 1);
        queryBuilder.append(")");
      }

      Query q =
          pm.newQuery("javax.jdo.query.SQL",
              queryBuilder.append(" GROUP BY SI.MID,BID ORDER BY SI.MID,BID ASC").toString());

      List resultList = (List) q.executeWithArray(parameters.toArray());
      Iterator iterator = resultList.iterator();
      while (iterator.hasNext()) {
        FulfilledQuantityModel fulfilledQuantityModel = new FulfilledQuantityModel();
        Object[] objects = (Object[]) iterator.next();
        fulfilledQuantityModel.setMaterialId((Long) objects[0]);
        BigDecimal fulfilledQuantity;
        if (objects[1] != null) {
          String batchId = (String) objects[1];
          fulfilledQuantityModel.setBatchId(batchId);
          fulfilledQuantity = (BigDecimal) objects[3];
          fulfilledQuantityModel.setFulfilledQuantity(fulfilledQuantity);
          fulfilledQuantityModel.setExpiryDate((Date) objects[5]);
          fulfilledQuantityModel.setManufacturedDate((Date) objects[6]);
          fulfilledQuantityModel.setManufacturer((String) objects[7]);
        } else {
          fulfilledQuantity = (BigDecimal) objects[2];
        }
        fulfilledQuantityModel.setFulfilledQuantity(fulfilledQuantity);
        fulfilledQuantityModelList.add(fulfilledQuantityModel);
      }
    } catch (Exception e) {
      xLogger.warn("Exception while getting fulfilled quantity", e);
      throw new ServiceException("Error fetching fulfilled quantity");

    }
    return fulfilledQuantityModelList;
  }

}
