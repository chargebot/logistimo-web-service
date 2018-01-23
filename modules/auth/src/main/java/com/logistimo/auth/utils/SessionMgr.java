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

/**
 *
 */
package com.logistimo.auth.utils;

import com.logistimo.AppFactory;
import com.logistimo.auth.service.AuthenticationService;
import com.logistimo.auth.service.impl.AuthenticationServiceImpl;
import com.logistimo.constants.Constants;
import com.logistimo.context.StaticApplicationContext;
import com.logistimo.logger.XLog;
import com.logistimo.security.SecureUserDetails;
import com.logistimo.services.cache.MemcacheService;
import com.logistimo.users.entity.IUserAccount;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;


/**
 * @author arun
 */
public class SessionMgr {

  public static final int SESSION_INACTIVEINTERVAL_DEFAULT = 604800; // seconds (7 days)
  private static final int
      TASK_SIZE =
      100;
  // 100 at a time (say, to remove session objects from datastore)

  private static final XLog xLogger = XLog.getLog(SessionMgr.class);

  /**
   * Initialize a user session after logging - typically used after logging in via the REST login API (not used for Service Manager login, which is managed through Spring security)
   * TODO: Move the RESTful login to use Spring security as well
   */
  public static void initSession(HttpSession session, IUserAccount user) {
    xLogger.fine("Entered initSession");
    // Get domain Id
    Long domainId = user.getDomainId();
    // Set the current domain for the user
    session.setAttribute(Constants.PARAM_DOMAINID, domainId);

    // Set the expirty interval
    ///if ( session.isNew() )
    ///	session.setMaxInactiveInterval( SESSION_INACTIVEINTERVAL_DEFAULT );
    ///xLogger.fine( "SessionMgr: SESSION: creation time = {0}, last-accessed-time = {1}, max-inactive-interval = {2}", new Date( session.getCreationTime() ), new Date( session.getLastAccessedTime() ), session.getMaxInactiveInterval() );

    xLogger.fine("Exiting initSession");
  }

  /**
   * Initialize a user after a Spring security
   */
  public static void initSession(HttpSession session, SecureUserDetails user) {
    user.setCurrentDomainId(user.getDomainId());
    // Set the user param
    session.setAttribute(Constants.PARAM_USER, user);
    SecurityUtils.setUserDetails(user);
    // Set the user parameters in the session
    Long domainId = user.getDomainId();
    session.setAttribute(Constants.PARAM_DOMAINID, domainId); // set the current domain
    //Clear existing sessions
    AuthenticationService as = StaticApplicationContext.getBean(AuthenticationServiceImpl.class);
    as.updateUserSession(user.getUsername(), session.getId());
  }

  /**
   * Recreating the session
   */
  public static void recreateSession(HttpServletRequest request, SecureUserDetails user) {
    HttpSession session = request.getSession(false);
    Map<String, Object> sessionAttirbutes = new HashMap<>();
    MemcacheService cacheService = AppFactory.get().getMemcacheService();

    if (session != null) {
      Enumeration sessionAttrEnum = session.getAttributeNames();
      if (sessionAttrEnum != null) {
        while (sessionAttrEnum.hasMoreElements()) {
          Object attribute = sessionAttrEnum.nextElement();
          sessionAttirbutes.put(attribute.toString(), session.getAttribute(attribute.toString()));
        }
      }
      //Deleteing session directly in Redis before invalidating it.
      cacheService.delete(session.getId());
      session.invalidate();
    }

    session = request.getSession(true);
    for (String attributeName : sessionAttirbutes.keySet()) {
      session.setAttribute(attributeName, sessionAttirbutes.get(attributeName));
    }
    //Initialzing the session
    initSession(session, user);
  }

  public static void cleanupSession(HttpSession session) {
    // Remove the pagination cursors, if available
    if (session.getAttribute(Constants.CURSOR_TRANSACTIONS) != null) {
      session.removeAttribute(Constants.CURSOR_TRANSACTIONS);
    }
    if (session.getAttribute(Constants.CURSOR_ORDERS) != null) {
      session.removeAttribute(Constants.CURSOR_ORDERS);
    }
    if (session.getAttribute(Constants.CURSOR_MATERIALS) != null) {
      session.removeAttribute(Constants.CURSOR_MATERIALS);
    }
    if (session.getAttribute(Constants.CURSOR_INVENTORY) != null) {
      session.removeAttribute(Constants.CURSOR_INVENTORY);
    }
    if (session.getAttribute(Constants.CURSOR_KIOSKS) != null) {
      session.removeAttribute(Constants.CURSOR_KIOSKS);
    }
    if (session.getAttribute(Constants.CURSOR_USERS) != null) {
      session.removeAttribute(Constants.CURSOR_USERS);
    }
    session.removeAttribute(Constants.PARAM_DOMAINID);
    session.removeAttribute(Constants.PARAM_USER);
    SecurityUtils.setUserDetails(null);
    // Invalidate session
    session.invalidate();
  }

  @Deprecated
  public static Long getCurrentDomain() {
    return SecurityUtils.getCurrentDomainId();
  }

  public static void setCurrentDomain(HttpSession session, Long domainId) {
    session.setAttribute(Constants.PARAM_DOMAINID, domainId);
    SecureUserDetails userDetails = (SecureUserDetails) session.getAttribute(Constants.PARAM_USER);
    userDetails.setCurrentDomainId(domainId);
    session.setAttribute(Constants.PARAM_USER, userDetails);
    SecurityUtils.setUserDetails(userDetails);
  }

  // Get and set pagination cursors
  @SuppressWarnings("unchecked")
  public static void setCursor(HttpSession session, String cursorType, int offset, String cursor) {
    // Get the cursors map for pagination
    Map<Integer, String> cursorMap = (Map<Integer, String>) session.getAttribute(cursorType);
    if (cursorMap == null) {
      cursorMap = new HashMap<>();
    } else if (offset == 1) {
      // Remove the older cursor map for the first page
      session.removeAttribute(cursorType);
      // Reset cursorMap
      cursorMap = new HashMap<>();
    }
    cursorMap.put(offset, cursor);
    session.setAttribute(cursorType, cursorMap);
  }

  @SuppressWarnings("unchecked")
  public static String getCursor(HttpSession session, String cursorType, int offset) {
    // Get the cursors map for pagination
    Map<Integer, String> cursorMap = (Map<Integer, String>) session.getAttribute(cursorType);
    if (cursorMap == null) {
      return null;
    }
    return cursorMap.get(offset);
  }

  // Delete all expired sessions, up to the specified limit per call
  public static void deleteSessions() {
            /*xLogger.fine( "Entered deleteSessions" );
            try {
    	// Get the datastore service
	    	DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
	    	Query q = new Query( "_ah_SESSION" ).setKeysOnly().setFilter( new Query.FilterPredicate( "_expires", 
	    																				FilterOperator.LESS_THAN,
	    																				new Date().getTime() ) );
	    	FetchOptions fetchOptions = FetchOptions.Builder.withLimit( TASK_SIZE ); // 100 at a time
	    	PreparedQuery pq = ds.prepare( q );
	    	List<Entity> sessions = pq.asList( fetchOptions );
	    	if ( sessions == null || sessions.isEmpty() ) {
	    		xLogger.info( "No expired sessions to delete" );
	    		return;
	    	}
	    	// Remove session objects from the datastore and the cache
	    	// Get cache and session object keys to remove
	    	MemcacheService cache = MemcacheServiceFactory.getMemcacheService();
	    	Iterator<Entity> it = sessions.iterator();
	    	List<String> cacheKeys = new ArrayList<String>();
	    	List<Key> dsKeys = new ArrayList<Key>(); 
	    	while ( it.hasNext() ) {
	    		Key key = it.next().getKey();
	    		cacheKeys.add( key.getName() );
	    		dsKeys.add( key );
	    	}
	    	// Remove from cache
	    	if ( cache != null )
	    		cache.deleteAll( cacheKeys );
	    	// Remove from datastore
	    	ds.delete( dsKeys );
	    	xLogger.info( "Removed {0} expired sessions", dsKeys.size() );
	    	// Check if we need to chain on this task, i.e. if more session objects can be expected
	    	if ( sessions.size() == TASK_SIZE ) {
	    		Map<String,String> params = new HashMap<String,String>();
	    		params.put( "action", "deletesessions" );
	    		TaskScheduler.schedule( TaskScheduler.QUEUE_DEFAULT, "/task/admin", params, TaskScheduler.METHOD_POST );
	    	}
    	} catch ( Exception e ) {
    		xLogger.severe( "{0} when deleting sessions: {1}", e.getClass().getName(), e.getMessage() );
    	}
    	xLogger.fine( "Exiting deleteSessions" );*/
  }
}
