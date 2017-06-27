package com.logistimo.config.models;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by naveensnair on 12/05/17.
 */
public class ApprovalsConfig implements Serializable {

  private static final String ORDER = "order";

  private OrderConfig orderConfig;

  public ApprovalsConfig(){
    orderConfig = new OrderConfig();
  }

  public ApprovalsConfig(JSONObject json) {
    try {
      orderConfig = new OrderConfig(json.getJSONObject(ORDER));
    } catch (JSONException e) {
      orderConfig = new OrderConfig();
    }
  }

  public OrderConfig getOrderConfig() {
    return orderConfig;
  }

  public void setOrderConfig(OrderConfig orderConfig) {
    this.orderConfig = orderConfig;
  }

  public JSONObject toJSONObject() throws ConfigurationException {
    try {
      JSONObject json = new JSONObject();
      if (orderConfig != null) {
        json.put(ORDER, orderConfig.toJSONObject());
      }
      return json;
    } catch (Exception e) {
      throw new ConfigurationException(e.getMessage());
    }
  }

  public static final String PURCHASE_SALES_ORDER_APPROVAL = "psoa";
  public static final String ENTITY_TAGS = "et";
  public static final String PURCHASE_ORDER_APPROVAL = "poa";
  public static final String TRANSFER_ORDER_APPROVAL = "toa";
  public static final String SALES_ORDER_APPROVAL = "soa";
  public static final String SALES_ORDER_APPROVAL_CREATION = "soac";
  public static final String SALES_ORDER_APPROVAL_SHIPPING = "soas";
  public static final String PRIMARY_APPROVERS = "pa";
  public static final String SECONDARY_APPROVERS = "sa";
  public static final String PURCHASE_ORDER_APPROVAL_EXPIRY = "px";
  public static final String SALES_ORDER_APPROVAL_EXPIRY = "sx";
  public static final String TRANSFER_ORDER_APPROVAL_EXPIRY = "tx";


  public static class OrderConfig implements Serializable{

    private List<PurchaseSalesOrderConfig> psoa = new ArrayList<>(1);
    private List<String> pa = new ArrayList<>(1); //primary approvers
    private List<String> sa = new ArrayList<>(1); //secondary approvers
    private int px; //purchase order approval expiry time
    private int sx; //sales order approval expiry time
    private int tx; //transfer order approval expiry time

    public List<PurchaseSalesOrderConfig> getPurchaseSalesOrderApproval() {
      return psoa;
    }

    public void setPurchaseSalesOrderApproval(
        List<PurchaseSalesOrderConfig> psoa) {
      this.psoa = psoa;
    }

    public List<String> getPrimaryApprovers() {
      return pa;
    }

    public void setPrimaryApprovers(List<String> pa) {
      this.pa = pa;
    }

    public List<String> getSecondaryApprovers() {
      return sa;
    }

    public void setSecondaryApprovers(List<String> sa) {
      this.sa = sa;
    }

    public int getPurchaseOrderApprovalExpiry() {
      return px;
    }

    public void setPurchaseOrderApprovalExpiry(int px) {
      this.px = px;
    }

    public int getSalesOrderApprovalExpiry() {
      return sx;
    }

    public void setSalesOrderApprovalExpiry(int sx) {
      this.sx = sx;
    }

    public int getTransferOrderApprovalExpiry() {
      return tx;
    }

    public void setTransferOrderApprovalExpiry(int tx) {
      this.tx = tx;
    }

    public Integer getExpiry(Integer orderType) {
      Integer expiry = 24;
      switch (orderType) {
        case 0:
          expiry = getTransferOrderApprovalExpiry();
          break;
        case 1:
          expiry = getPurchaseOrderApprovalExpiry();
          break;
        case 2:
          expiry = getSalesOrderApprovalExpiry();
          break;
        default:
          break;
      }
      return expiry;
    }

    public boolean isPurchaseApprovalEnabled(List<String> entityTags) {
      return psoa.stream()
          .filter(config -> entityTags.stream()
              .anyMatch(tag -> config.getEntityTags().contains(tag)))
          .filter(PurchaseSalesOrderConfig::isPurchaseOrderApproval)
          .findFirst().isPresent();
    }

    public boolean isSaleApprovalEnabled(List<String> entityTags) {
      return psoa.stream()
          .filter(config -> entityTags.stream()
              .anyMatch(tag -> config.getEntityTags().contains(tag)))
          .filter(PurchaseSalesOrderConfig::isSalesOrderApproval)
          .findFirst().isPresent();
    }

    public OrderConfig(){

    }

    public OrderConfig(JSONObject jsonObject) {
      if(jsonObject != null && jsonObject.length() > 0) {
        try{
          JSONArray primaryArray = jsonObject.getJSONArray(PRIMARY_APPROVERS);
          List<String> primaryApprovers = new ArrayList<>();
          for(int i=0; i<primaryArray.length(); i++) {
            primaryApprovers.add(primaryArray.get(i).toString());
          }
          pa = primaryApprovers;
        }catch(Exception e) {
          pa = null;
        }

        try{
          JSONArray secondaryArray = jsonObject.getJSONArray(SECONDARY_APPROVERS);
          List<String> secondaryApprovers = new ArrayList<>();
          for(int i=0; i<secondaryArray.length(); i++) {
            secondaryApprovers.add(secondaryArray.get(i).toString());
          }
          sa = secondaryApprovers;
        } catch (Exception e) {
          sa = null;
        }

        if(jsonObject.get(PURCHASE_SALES_ORDER_APPROVAL) != null) {
          JSONArray jsonArray = jsonObject.getJSONArray(PURCHASE_SALES_ORDER_APPROVAL);
          List<PurchaseSalesOrderConfig> purchaseSalesOrderConfigs = new ArrayList<>();
          for(int i=0; i<jsonArray.length(); i++) {
            PurchaseSalesOrderConfig ps = new PurchaseSalesOrderConfig(jsonArray.getJSONObject(i));
            purchaseSalesOrderConfigs.add(ps);
          }
          psoa = purchaseSalesOrderConfigs;
        }
      }
    }

    public JSONObject toJSONObject() {
      JSONObject json = new JSONObject();
      if(pa != null && pa.size() > 0) {
        json.put(PRIMARY_APPROVERS, pa);
      }
      if(sa != null && sa.size() > 0) {
        json.put(SECONDARY_APPROVERS, sa);
      }
      if(psoa != null && psoa.size() > 0) {
        JSONArray jsonArray = new JSONArray();
        for(PurchaseSalesOrderConfig purchaseSalesOrderConfig : psoa) {
          JSONObject jsonObject = purchaseSalesOrderConfig.toJSONObject();
          jsonArray.put(jsonObject);
        }
        if(jsonArray.length() > 0) {
          json.put(PURCHASE_SALES_ORDER_APPROVAL, jsonArray);
        }
      }

      return json;
    }

  }


  public static class PurchaseSalesOrderConfig implements Serializable{
    private List<String> et = new ArrayList<>(1); //entity tags
    private boolean poa; //purchase order approval
    private boolean soa; //purchase order approval
    private boolean soac; //sales order approval during creation
    private boolean soas; //sales order approval during shipping

    public PurchaseSalesOrderConfig() {

    }

    public PurchaseSalesOrderConfig(JSONObject jsonObject) {
      if (jsonObject != null && jsonObject.length() > 0) {
        try {
          JSONArray jsonArray = jsonObject.getJSONArray(ENTITY_TAGS);
          et = new ArrayList<>();
          for(int i=0; i<jsonArray.length(); i++) {
            Object obj = jsonArray.get(i);
            et.add((String) obj);
          }

        } catch (JSONException e) {
          // do nothing
        }

        if (jsonObject.get(SALES_ORDER_APPROVAL) != null) {
          soa = jsonObject.getBoolean(SALES_ORDER_APPROVAL);
        }
        if (jsonObject.get(PURCHASE_ORDER_APPROVAL) != null) {
          poa = jsonObject.getBoolean(PURCHASE_ORDER_APPROVAL);
        }
        if (jsonObject.get(SALES_ORDER_APPROVAL_CREATION) != null) {
          soac = jsonObject.getBoolean(SALES_ORDER_APPROVAL_CREATION);
        }
        if (jsonObject.get(SALES_ORDER_APPROVAL_SHIPPING) != null) {
          soas = jsonObject.getBoolean(SALES_ORDER_APPROVAL_SHIPPING);
        }
      }
    }

    public JSONObject toJSONObject() throws JSONException{
      JSONObject json = new JSONObject();
      json.put(ENTITY_TAGS, et);
      json.put(PURCHASE_ORDER_APPROVAL, poa);
      json.put(SALES_ORDER_APPROVAL, soa);
      json.put(SALES_ORDER_APPROVAL_CREATION, soac);
      json.put(SALES_ORDER_APPROVAL_SHIPPING, soas);
      return json;
    }

    public List<String> getEntityTags() {
      return et;
    }

    public void setEntityTags(List<String> et) {
      this.et = et;
    }

    public boolean isPurchaseOrderApproval() {
      return poa;
    }

    public void setPurchaseOrderApproval(boolean poa) {
      this.poa = poa;
    }

    public boolean isSalesOrderApproval() { return soa; }

    public void setSalesOrderApproval(boolean soa) { this.soa = soa; }

  }
}