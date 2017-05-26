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

package com.logistimo.api.models;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by charan on 03/11/15.
 */
public class InvDashboardModel implements Serializable {
  /**
   * State or District or Kiosk name
   */
  public String dim;
  /**
   * Material wise type distribution
   */
  public Map<Long, EventDistribModel> eve = new HashMap<>(3);
  /**
   * Dimension id.. applicable only for Kiosk at this time.
   */
  public long id;

  public Map<String, Integer> assets = new HashMap<>(4);
}
