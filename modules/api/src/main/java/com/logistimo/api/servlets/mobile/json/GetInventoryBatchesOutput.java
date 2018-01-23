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

package com.logistimo.api.servlets.mobile.json;

import com.google.gson.annotations.Expose;

import com.logistimo.inventory.entity.IInvntryBatch;

import java.util.List;

public class GetInventoryBatchesOutput extends JsonOutput {

  @Expose
  private String mid = null; // material Id
  @Expose
  private List<IInvntryBatch> btchs = null; // batches

  public GetInventoryBatchesOutput(String version, boolean status, String message,
                                   String materialId, List<IInvntryBatch> batches) {
    super(version, status, message);
    this.mid = materialId;
    this.btchs = batches;
  }

  public String getMaterialId() {
    return mid;
  }

  public List<IInvntryBatch> getBatches() {
    return btchs;
  }
}
