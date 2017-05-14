package com.logistimo.api.servlets;

import com.logistimo.AppFactory;
import com.logistimo.services.mapred.IMapredService;

import com.logistimo.services.mapred.MapReduceConstants;
import com.logistimo.logger.XLog;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Arun
 */
@SuppressWarnings("serial")
public class MapReduceStarterServlet extends HttpServlet {

  // Logger
  private static final XLog xLogger = XLog.getLog(MapReduceStarterServlet.class);
  private static final String ALL = "all";
  private final String[]
      kinds =
      {"Kiosk", "Material", "Invntry", "InvntryBatch", "InvntryItem", "KioskLink", "Order",
          "DemandItem",
          "DemandItemBatch", "Transaction", "InvntryLog", "InvntryEvntLog", "BBoard", "Event",
          "MnlTransaction"};

  // Process the GET request
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    xLogger.fine("Entered doGet");
    // Get the type of mappers class
    String name = request.getParameter("name"); // configuration name in mapreduce.xml
    // Get the entity kind to map on
    String
        kind =
        request.getParameter("kind"); // simple name of entity class (the one to be mapped on)
    String domainId = request.getParameter("domainId");
    String rerun = request.getParameter("rerun");
    if (name == null || name.isEmpty()) {
      throw new IOException("No mapreduce configuration name specified");
    }
    if ("DeleteAllEntities".equals(name) && domainId == null) {
      throw new IOException("Domain Id required for triggering delete entity");
    }
    if ("DomainIdListMigrator".equals(name) && (kind == null || kind.isEmpty())) {
      kind = ALL;
    }
    if (kind == null || kind.isEmpty()) {
      throw new IOException("No entity kind specified (to map on)");
    }
    // Start MapReduce job
    try {
      if (ALL.equalsIgnoreCase(kind)) {
        for (String k : kinds) {
          startJob(name, k, domainId, rerun);
        }
      } else {
        startJob(name, kind, domainId, rerun);
      }
    } catch (Exception e) { // we don't any exceptions thrown, and a response sent back to user
      xLogger.severe("Exception: {0} : {1}", e.getClass().getName(), e.getMessage());
    }
    xLogger.fine("Exiting doGet");
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    doGet(request, response);
  }

  // Start job using the start_job command
  private void startJob(String configName, String kind, String domainId, String rerun)
      throws IOException {
    xLogger.fine("Entered startJob");
    // Create the param. map
    Map<String, String> params = new HashMap<String, String>();
    params.put(IMapredService.PARAM_ENTITYKIND, kind);
    if (domainId != null && "DeleteAllEntities".equals(configName)) {
      params.put(MapReduceConstants.DOMAIN_ID, domainId);
    }
    if (rerun != null) {
      params.put(MapReduceConstants.RERUN, rerun);
    }
    // Start the MR job
    AppFactory.get().getMapredService().startJob(configName, params);
    xLogger.fine("Exiting startJob");
  }
}
