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

package com.logistimo.api.servlets;

import com.logistimo.AppFactory;
import com.logistimo.auth.service.AuthenticationService;
import com.logistimo.communications.service.EmailService;
import com.logistimo.config.models.GeneralConfig;
import com.logistimo.constants.Constants;
import com.logistimo.context.StaticApplicationContext;
import com.logistimo.domains.service.DomainsService;
import com.logistimo.domains.service.impl.DomainsServiceImpl;
import com.logistimo.entities.entity.IKiosk;
import com.logistimo.entities.service.EntitiesService;
import com.logistimo.entities.service.EntitiesServiceImpl;
import com.logistimo.exception.InvalidServiceException;
import com.logistimo.exception.UnauthorizedException;
import com.logistimo.logger.XLog;
import com.logistimo.pagination.PageParams;
import com.logistimo.pagination.Results;
import com.logistimo.proto.RestConstantsZ;
import com.logistimo.services.ServiceException;
import com.logistimo.services.Services;
import com.logistimo.services.taskqueue.ITaskService;
import com.logistimo.users.entity.IUserAccount;
import com.logistimo.users.service.UsersService;
import com.logistimo.users.service.impl.UsersServiceImpl;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by mohansrinivas on 1/25/16.
 */
public class FeedbackServlet extends HttpServlet {
  public static final String FEEDBACK_API_URL = "/api/feedback";
  public static final String VELOCITY_TEMPLATE_PATH = "velocity/Feedback.vm";
  private static final XLog _LOGGER = XLog.getLog(FeedbackServlet.class);

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, java.io.IOException {
    String strUserId = request.getParameter(RestConstantsZ.USER_ID);
    String data = request.getParameter("text");
    String title = request.getParameter("title");
    IUserAccount account;
    if (!"true".equals(request.getParameter("execute"))) {
      try {
        AuthenticationService as = StaticApplicationContext.getBean(AuthenticationService.class);
        as.authenticateToken(request.getHeader(Constants.TOKEN), 0);
        Map<String, String> params = new HashMap<>(4);
        params.put("text", data);
        params.put("uid", strUserId);
        params.put("title", title);
        params.put("execute", "true");
        AppFactory.get().getTaskService()
            .schedule(ITaskService.QUEUE_DEFAULT, FEEDBACK_API_URL, params,
                ITaskService.METHOD_POST);
      } catch (ServiceException | UnauthorizedException e) {
        _LOGGER.warn("Failed to authenticate user {0} for the feedback", strUserId, e);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      } catch (Exception e) {
        _LOGGER.warn("Failed to submit the feedback", e);
        throw new InvalidServiceException("Failed to submit the feedback");
      }
      return;
    }
    try {
      VelocityEngine ve = new VelocityEngine();
      ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
      ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
      ve.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, NullLogChute.class.getName());
      ve.setProperty("runtime.log.logsystem.log4j.logger", _LOGGER);
      ve.setProperty("input.encoding", "UTF-8");
      ve.setProperty("output.encoding", "UTF-8");
      ve.init();

      String eName = null;
      String village = null;
      String state = null;
      String district = null;

      UsersService as = Services.getService(UsersServiceImpl.class);
      EntitiesService entitiesService = Services.getService(EntitiesServiceImpl.class);
      account = as.getUserAccount(strUserId);

      GeneralConfig gc = GeneralConfig.getInstance();
      String feedbackAddress = gc.getFeedbackEmail();

      String userName = account.getFullName();
      String mPhone = account.getMobilePhoneNumber();
      String eMail = account.getEmail();
      Long domainId = account.getDomainId();

      Results results = entitiesService.getKiosksForUser(account, null, new PageParams(1));
      IKiosk userKiosk = null;
      if (results.getNumFound() > 0) {
        userKiosk = (IKiosk) results.getResults().get(0);
        if (userKiosk != null) {
          eName = userKiosk.getName();
          village = userKiosk.getCity();
          state = userKiosk.getState();
          district = userKiosk.getDistrict();
        }
      }

      DomainsService ds = Services.getService(DomainsServiceImpl.class);
      String domainName = ds.getDomain(domainId).getName();
      String subject = "[feedback]" + " From " + userName + " in " + domainName;

      VelocityContext vc = new VelocityContext();
      vc.put("userId", strUserId);
      vc.put("domain", domainName);
      vc.put("userName", userName);
      vc.put("message", data);
      vc.put("mPhone", mPhone);
      if (eMail != null && !eMail.isEmpty()) {
        vc.put("eMail", eMail);
      }

      if (userKiosk != null) {
        vc.put("eName", eName);
        vc.put("village", village);
        vc.put("state", state);
        if (district != null && !district.isEmpty()) {
          vc.put("district", district);
        }
      }
      if (title != null && !title.isEmpty()) {
        vc.put("title", title);
      }
      StringWriter out = new StringWriter();

      Template template = ve.getTemplate(VELOCITY_TEMPLATE_PATH, "UTF-8");
      template.merge(vc, out);

      EmailService svc = EmailService.getInstance();
      svc.sendHTML(domainId, Collections.singletonList(feedbackAddress), subject, out.toString(),
          null);
      _LOGGER
          .info("Feedback Submitted from user {0} with subject {1} and feedback ds {2}", strUserId,
              subject, data);
    } catch (Exception ex) {
      _LOGGER.warn("Invalid feedback data ", ex);
    }
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, java.io.IOException {
    throw new ServletException(
        "GET method used with " + getClass().getName() + ": POST method required.");
  }
}
