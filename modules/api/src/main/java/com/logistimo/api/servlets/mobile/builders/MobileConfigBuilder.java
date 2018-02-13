/*
 * Copyright © 2017 Logistimo.
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

package com.logistimo.api.servlets.mobile.builders;

import com.logistimo.config.models.AccountingConfig;
import com.logistimo.config.models.ApprovalsConfig;
import com.logistimo.config.models.ReturnsConfig;
import com.logistimo.context.StaticApplicationContext;
import com.logistimo.proto.MobileAccountingConfigModel;
import com.logistimo.proto.MobileApprovalsConfigModel;
import com.logistimo.proto.MobileApproversModel;
import com.logistimo.proto.MobilePurchaseSalesOrdersApprovalModel;

import org.apache.commons.collections.CollectionUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by vani on 28/06/17.
 */
public class MobileConfigBuilder {
  private static final int ENABLED = 1;
  private static final int DISABLED = 0;

  /**
   * Builds the approval configuration model as required by the mobile from approvals configuration as obtained from domain configuration
   * @param approvalsConfig
   * @return
   */
  public MobileApprovalsConfigModel buildApprovalConfiguration(ApprovalsConfig approvalsConfig) {
    if (approvalsConfig == null) {
      return null;
    }
    MobileApprovalsConfigModel mobileApprovalsConfigModel = new MobileApprovalsConfigModel();
    ApprovalsConfig.OrderConfig ordersConfig = approvalsConfig.getOrderConfig();
    mobileApprovalsConfigModel.ords = buildPurchaseSalesOrderApprovalConfigModel(
        ordersConfig);
    mobileApprovalsConfigModel.trf = buildTransfersApprovalConfigModel(ordersConfig);
    return mobileApprovalsConfigModel;
  }

  /**
   * Builds the accounting configuration model as required by the mobile from accounting configuration as obtained from domain configuration
   * @param isAccountingEnabled
   * @param accountingConfig
   * @return
   */
  public MobileAccountingConfigModel buildAccountingConfiguration(boolean isAccountingEnabled, AccountingConfig accountingConfig) {
    MobileAccountingConfigModel mobileAccountingConfigModel = new MobileAccountingConfigModel();
    mobileAccountingConfigModel.enb = isAccountingEnabled;
    if (isAccountingEnabled) {
      if (accountingConfig.enforceConfirm()) {
        mobileAccountingConfigModel.enfcrl = MobileAccountingConfigModel.ENFORCE_CREDIT_LIMIT_ON_CONFIRM_ORDER;
      } else if (accountingConfig.enforceShipped()) {
        mobileAccountingConfigModel.enfcrl = MobileAccountingConfigModel.ENFORCE_CREDIT_LIMIT_ON_SHIP_ORDER;
      }
    }
    return mobileAccountingConfigModel;
  }

  private Map<String, MobilePurchaseSalesOrdersApprovalModel> buildPurchaseSalesOrderApprovalConfigModel(
      ApprovalsConfig.OrderConfig orderConfig) {
    if (orderConfig == null) {
      return null;
    }
    List<ApprovalsConfig.PurchaseSalesOrderConfig>
        psoConfigList =
        orderConfig.getPurchaseSalesOrderApproval();
    if (psoConfigList == null || psoConfigList.isEmpty()) {
      return null;
    }
    // Iterate through each item in the list and build the model
    Map<String, MobilePurchaseSalesOrdersApprovalModel>
        ordersApprovalConfigModelMap =
        new HashMap<>(psoConfigList.size());
    for (ApprovalsConfig.PurchaseSalesOrderConfig psoConfig : psoConfigList) {
      boolean isPOApproval = psoConfig.isPurchaseOrderApproval();
      boolean isSOApproval = psoConfig.isSalesOrderApproval();
      List<String> eTags = psoConfig.getEntityTags();
      if (eTags != null && !eTags.isEmpty()) {
        for (String eTag : eTags) {
          MobilePurchaseSalesOrdersApprovalModel
              mobPurSleOrdApprvlModel = new MobilePurchaseSalesOrdersApprovalModel();
          mobPurSleOrdApprvlModel.prc = new MobileApprovalsConfigModel.MobileOrderApprovalModel();
          mobPurSleOrdApprvlModel.sle = new MobileApprovalsConfigModel.MobileOrderApprovalModel();
          mobPurSleOrdApprvlModel.prc.enb = isPOApproval ? ENABLED : DISABLED;
          mobPurSleOrdApprvlModel.sle.enb = isSOApproval ? ENABLED : DISABLED;
          ordersApprovalConfigModelMap.put(eTag, mobPurSleOrdApprvlModel);
        }
      }
    }
    return ordersApprovalConfigModelMap;
  }

  private MobileApprovalsConfigModel.MobileTransfersApprovalModel buildTransfersApprovalConfigModel(
      ApprovalsConfig.OrderConfig orderConfig) {
    if (orderConfig == null) {
      return null;
    }
    MobileApprovalsConfigModel.MobileTransfersApprovalModel
        transfersApprovalModel =
        new MobileApprovalsConfigModel.MobileTransfersApprovalModel();
    List<String> primaryApprovers = orderConfig.getPrimaryApprovers();
    List<String> secondaryApprovers = orderConfig.getSecondaryApprovers();
    if ((primaryApprovers == null || primaryApprovers.isEmpty()) && (secondaryApprovers == null
        || secondaryApprovers.isEmpty())) {
      transfersApprovalModel.enb = DISABLED;
      return transfersApprovalModel;
    }
    transfersApprovalModel.enb = ENABLED;
    transfersApprovalModel.apprvrs =
        buildTransfersApproversModel(primaryApprovers, secondaryApprovers);
    return transfersApprovalModel;
  }

  private MobileApproversModel buildTransfersApproversModel(List<String> primaryApprovers,
                                                            List<String> secApprovers) {
    if ((primaryApprovers == null || primaryApprovers.isEmpty()) && (secApprovers == null
        || secApprovers.isEmpty())) {
      return null;
    }
    MobileUserBuilder mobileUserBuilder = StaticApplicationContext.getBean(MobileUserBuilder.class);
    MobileApproversModel approversModel = new MobileApproversModel();
    if (primaryApprovers != null && !primaryApprovers.isEmpty()) {
      approversModel.prm =
          mobileUserBuilder
              .buildMobileUserModels(mobileUserBuilder.constructUserAccount(primaryApprovers));
    }
    if (secApprovers != null && !secApprovers.isEmpty()) {
      approversModel.scn =
          mobileUserBuilder
              .buildMobileUserModels(mobileUserBuilder.constructUserAccount(secApprovers));
    }
    return approversModel;
  }

  public List<MobileReturnsConfigModel> buildMobileReturnsConfigModels(List<ReturnsConfig> returnsConfigList) {
    if (CollectionUtils.isEmpty(returnsConfigList)) {
      return Collections.emptyList();
    }
    return returnsConfigList.stream()
        .map(this::buildReturnsConfigModel)
        .collect(Collectors.toList());
  }

  public MobileReturnsConfigModel buildReturnsConfigModel(ReturnsConfig returnsConfig) {
    MobileReturnsConfigModel mobileReturnsConfigModel = new MobileReturnsConfigModel();
    if (returnsConfig == null) {
      return mobileReturnsConfigModel;
    }
    mobileReturnsConfigModel.setEntityTags(returnsConfig.getEntityTags());
    mobileReturnsConfigModel.setIncomingDuration(returnsConfig.getIncomingDuration());
    mobileReturnsConfigModel.setOutgoingDuration(returnsConfig.getOutgoingDuration());
    return mobileReturnsConfigModel;
  }
}
