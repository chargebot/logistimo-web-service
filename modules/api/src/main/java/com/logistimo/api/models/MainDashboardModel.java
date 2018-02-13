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

import com.logistimo.models.BaseResponseModel;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Mohan Raja
 */
public class MainDashboardModel extends BaseResponseModel implements Serializable {
  public Map<String, Long> invDomain;
  public Map<String, Long> entDomain;
  public Map<String, Long> tempDomain;
  public Map<String, Map<String, DashboardChartModel>> inv;
  public Map<String, Map<String, DashboardChartModel>> ent;
  public Map<String, Map<String, DashboardChartModel>> temp;
  public String mTy;
  public String mTyNm;
  public String mLev;
  public String mPTy;
  public Map<String, InvDashboardModel> invByMat;
  public String ut; // Updated time
  public DashboardChartModel pred; // Predictive: Overall predictive data on overall dashboard
}
