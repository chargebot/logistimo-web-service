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

package com.logistimo.api.controllers;

import com.logistimo.AppFactory;
import com.logistimo.api.builders.DomainBuilder;
import com.logistimo.api.models.superdomains.DomainDBModel;
import com.logistimo.api.models.superdomains.DomainModel;
import com.logistimo.auth.GenericAuthoriser;
import com.logistimo.auth.SecurityConstants;
import com.logistimo.auth.SecurityMgr;
import com.logistimo.auth.utils.SecurityUtils;
import com.logistimo.auth.utils.SessionMgr;
import com.logistimo.config.models.DomainConfig;
import com.logistimo.dao.JDOUtils;
import com.logistimo.domains.entity.IDomain;
import com.logistimo.domains.entity.IDomainLink;
import com.logistimo.domains.entity.IDomainPermission;
import com.logistimo.domains.service.DomainsService;
import com.logistimo.domains.service.impl.DomainsServiceImpl;
import com.logistimo.domains.utils.DomainDeleter;
import com.logistimo.exception.BadRequestException;
import com.logistimo.exception.InvalidDataException;
import com.logistimo.exception.InvalidServiceException;
import com.logistimo.exception.UnauthorizedException;
import com.logistimo.logger.XLog;
import com.logistimo.pagination.Navigator;
import com.logistimo.pagination.PageParams;
import com.logistimo.security.SecureUserDetails;
import com.logistimo.services.ObjectNotFoundException;
import com.logistimo.services.Resources;
import com.logistimo.services.ServiceException;
import com.logistimo.services.Services;
import com.logistimo.services.taskqueue.ITaskService;
import com.logistimo.users.entity.IUserAccount;
import com.logistimo.users.service.UsersService;
import com.logistimo.users.service.impl.UsersServiceImpl;
import com.logistimo.utils.LocalDateUtil;
import com.logistimo.utils.MsgUtil;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Mohan Raja
 */
@Controller
@RequestMapping("/domain")
public class DomainController {
  private static final XLog xLogger = XLog.getLog(DomainController.class);
  private static final String REMOVE_DOMAIN_TASK_URL = "/s2/api/domain/delete";
  private DomainBuilder builder = new DomainBuilder();

  @RequestMapping(value = "/delete", method = RequestMethod.POST)
  public
  @ResponseBody
  String deleteDomain(@RequestParam Long dId, HttpServletRequest request) {
    try {
      if (dId == null) {
        throw new ServiceException("No Domain is selected for removing");
      }
      boolean execute = request.getParameter("execute") != null;
      if (!execute) {
        String message = DomainDeleter.validateDeleteDomain(dId);
        if (!message.equals("success")) {
          return message;
        }
        Map<String, String> params = new HashMap<>();
        params.put("execute", "true");
        params.put("dId", String.valueOf(dId));
        try {
          AppFactory.get().getTaskService()
              .schedule(ITaskService.QUEUE_DEFAULT, REMOVE_DOMAIN_TASK_URL, params,
                  ITaskService.METHOD_POST);
        } catch (Exception e) {
          xLogger.warn("{0} when scheduling task to remove domain {1}: {2}", e.getClass().getName(),
              dId, e.getMessage(), e);
        }
        return "Delete domain scheduled successfully." + MsgUtil.newLine() + MsgUtil.newLine() +
            "Note: Deleting domain will take some time to complete.";
      }
      DomainDeleter.deleteDomain(dId);
      return null;
    } catch (ServiceException e) {
      xLogger.severe("Error in scheduling entity move:", e);
      throw new InvalidServiceException("Error in scheduling entity move");
    }
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  public
  @ResponseBody
  DomainDBModel getDomainById(@RequestParam Long domainId) {
    if (domainId == null) {
      throw new InvalidDataException("Invalid domain id");
    }
    try {
      DomainsService ds = Services.getService(DomainsServiceImpl.class);
      return builder.buildDomainModel(ds.getDomain(domainId));
    } catch (ServiceException | ObjectNotFoundException e) {
      xLogger.severe("Unable to fetch the details of the switching domain", e);
      throw new InvalidServiceException("Unable to fetch the details of the switching domain");
    }
  }

  @RequestMapping(value = "/domain", method = RequestMethod.GET)
  public
  @ResponseBody
  DomainModel fetchDomainById(@RequestParam Long domainId, HttpServletRequest request) {
    try {
      if (domainId == null) {
        throw new ServiceException("Domain is not available");
      }
      SecureUserDetails user = SecurityUtils.getUserDetails(request);
      Locale locale = user.getLocale();
      DomainsService ds = Services.getService(DomainsServiceImpl.class);
      IDomain domain = ds.getDomain(domainId);
      DomainModel dm = new DomainModel();
      UsersService as = Services.getService(UsersServiceImpl.class, locale);
      IUserAccount ub;
      IUserAccount cb;
      if (domain.getCreatedOn() != null) {
        dm.createdOn =
            LocalDateUtil.format(domain.getCreatedOn(), user.getLocale(), user.getTimezone());
      }
      if (domain.getLastUpdatedOn() != null) {
        dm.lastUpdatedOn =
            LocalDateUtil.format(domain.getLastUpdatedOn(), user.getLocale(), user.getTimezone());
      }
      boolean iMan = SecurityConstants.ROLE_SERVICEMANAGER.equals(user.getRole());
      dm.name = domain.getName();
      dm.description = domain.getDescription();
      dm.isActive = domain.isActive();
      if (domain.getLastUpdatedBy() != null) {
        try {
          ub = as.getUserAccount(domain.getLastUpdatedBy());
          dm.lastUpdatedByn = ub.getFullName();
          dm.lastUpdatedBy = domain.getLastUpdatedBy();
        } catch (Exception e) {
          xLogger.warn("Exception while getting domain updated by userdetails", e);
        }
      }
      if (domain.getOwnerId() != null) {
        try {
          cb = as.getUserAccount(domain.getOwnerId());
          dm.ownerName = cb.getFullName();
          dm.ownerId = domain.getOwnerId();
        } catch (Exception e) {
          xLogger.warn("Exception while getting domain created by user details", e);
        }
      }
      dm.dId = domain.getId();
      IDomainPermission permission = ds.getLinkedDomainPermission(domainId);
      DomainConfig dc = DomainConfig.getInstance(domainId);
      if (permission != null) {
        DomainModel
            pdm =
            builder.buildDomain(domain, null, permission, dc, false, false, false, iMan);
        dm.dp = pdm.dp;
      }
      return dm;
    } catch (ServiceException | ObjectNotFoundException e) {
      xLogger.severe("unable to get the get the details for domainId", e);
      throw new InvalidServiceException("unable to get the get the details for domainId");
    }
  }

  @RequestMapping(value = "/suggestions", method = RequestMethod.GET)
  public
  @ResponseBody
  List<IDomain> getDomainSuggestions(@RequestParam(required = false) String q,
                                     HttpServletRequest request) {
    try {
      DomainsService ds = Services.getService(DomainsServiceImpl.class);
      Navigator
          navigator =
          new Navigator(request.getSession(), "DomainController.getDomainSuggestions", 0, 10,
              "dummy", 0);
      PageParams pageParams = new PageParams(navigator.getCursor(0), 0, 10);
      List<IDomain> domains = ds.getDomains(q == null ? "" : q, pageParams);
      Long domainId = SecurityUtils.getDomainId();
      for (int i = 0; i < domains.size(); i++) {
        if (domains.get(i).getId().equals(domainId)) {
          domains.remove(i);
          break;
        }
      }
      return domains;
    } catch (ServiceException | ObjectNotFoundException e) {
      xLogger.severe("Unable to fetch the details of the switching domain", e);
      throw new InvalidServiceException("Unable to fetch the details of the switching domain");
    }
  }

  @RequestMapping(value = "/create", method = RequestMethod.POST)
  public
  @ResponseBody
  String createDomain(@RequestParam String domainName, @RequestParam(required = false) String desc,
                      HttpServletRequest request) {
    if (StringUtils.isEmpty(domainName)) {
      throw new InvalidDataException("Domain name can't be empty.");
    }
    try {
      SecureUserDetails sUser = SecurityMgr.getUserDetails(request.getSession());
      String userId = sUser.getUsername();
      DomainsService ds = Services.getService(DomainsServiceImpl.class);
      IDomain domain = JDOUtils.createInstance(IDomain.class);
      domain.setName(domainName);
      domain.setOwnerId(userId);
      domain.setLastUpdatedBy(userId);
      if (desc != null && !desc.isEmpty()) {
        domain.setDescription(desc);
      }
      Long domainId = ds.addDomain(domain);
      ds.createDefaultDomainPermissions(domainId);
      return "Domain " + MsgUtil.bold(domainName) + " created successfully.";
    } catch (ServiceException e) {
      xLogger.severe("Unable to create domain", e);
      throw new InvalidServiceException("Unable to create domain");
    }
  }

  @RequestMapping(value = "/current", method = RequestMethod.GET)
  public
  @ResponseBody
  DomainDBModel getCurrentDomain(HttpServletRequest request) {
    SecureUserDetails sUser = SecurityUtils.getUserDetails(request);
    Long domainId = SessionMgr.getCurrentDomain(request.getSession(), sUser.getUsername());
    return getDomainById(domainId);
  }

  @RequestMapping(value = "/currentUser", method = RequestMethod.GET)
  public
  @ResponseBody
  DomainDBModel getCurrentUserDomain(HttpServletRequest request) {
    SecureUserDetails sUser = SecurityUtils.getUserDetails(request);
    return getDomainById(sUser.getDomainId());
  }

  @RequestMapping(value = "/updateDomain/{domainId}", method = RequestMethod.GET)
  public
  @ResponseBody
  void updateDomain(@PathVariable Long domainId, @RequestParam String name,
                    @RequestParam String desc, HttpServletRequest request) throws ServiceException {
    xLogger.fine("updating the domain information");
    if (domainId == null) {
      throw new ServiceException("Invalid domain");
    }
    SecureUserDetails sUser = SecurityMgr.getUserDetails(request.getSession());
    String userId = sUser.getUsername();
    try {
      if (StringUtils.isNotEmpty(name) || StringUtils.isNotEmpty(desc)) {
        //     IDomain domain = JDOUtils.getObjectById(IDomain.class, domainId);
        DomainsService ds = Services.getService(DomainsServiceImpl.class);
        IDomain domain = ds.getDomain(domainId);
        domain.setName(name);
        domain.setDescription(desc);
        domain.setLastUpdatedBy(userId);
        ds.updateDomain(domain);
      }
    } catch (ServiceException | ObjectNotFoundException e) {
      xLogger.severe("Unable to update domain", e);
      throw new InvalidServiceException("Unable to update domain");
    }

  }


  @RequestMapping(value = "/switch", method = RequestMethod.POST)
  public
  @ResponseBody
  void switchDomain(@RequestBody String domainIdStr, HttpServletRequest request) {
    SecureUserDetails sUser = SecurityUtils.getUserDetails();
    boolean authorisedUser = false;
    Locale locale = sUser.getLocale();
    ResourceBundle backendMessages = Resources.get().getBundle("BackendMessages", locale);
    if (!GenericAuthoriser.authoriseAdmin(request)) {
      throw new UnauthorizedException(backendMessages.getString("permission.denied"));
    }
    if (StringUtils.isBlank(domainIdStr)) {
      xLogger.severe("Error in switching domain");
      throw new BadRequestException(backendMessages.getString("switch.domain.error"));
    }
    Long domainId = Long.parseLong(domainIdStr);
    try {
      UsersService as = Services.getService(UsersServiceImpl.class);
      List<Long> accDids = as.getUserAccount(sUser.getUsername()).getAccessibleDomainIds();
      if (sUser.getRole().equals(SecurityConstants.ROLE_SUPERUSER) || accDids.contains(domainId)) {
        authorisedUser = true;
      } else if (sUser.getRole().equals(SecurityConstants.ROLE_DOMAINOWNER)) {
        DomainsService ds = Services.getService(DomainsServiceImpl.class);
        List<IDomainLink> domains = ds.getDomainLinks(domainId, IDomainLink.TYPE_PARENT, -1);
        for (IDomainLink i : domains) {
          if (i.getLinkedDomainId() != null && accDids.contains(i.getLinkedDomainId())) {
            authorisedUser = true;
            break;
          }
        }
      } else {
        authorisedUser = false;
      }
    } catch (Exception e) {
      xLogger
          .warn("Error in fetching list of parent domains for domain: {0} for user: {1}", domainId,
              sUser.getUsername(), e);
    }
    if (!authorisedUser) {
      throw new UnauthorizedException(backendMessages.getString("permission.denied"));
    }
  }
}
