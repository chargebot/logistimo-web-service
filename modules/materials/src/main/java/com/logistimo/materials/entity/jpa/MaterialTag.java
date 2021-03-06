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

package com.logistimo.materials.entity.jpa;

import com.logistimo.tags.entity.jpa.Tag;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import lombok.Data;

/**
 * Created by charan on 02/04/18.
 */
@Data
@Entity
@Table(name = "MATERIAL_TAGS")
public class MaterialTag implements Serializable {
  @Id
  @Column(name = "MATERIALID", insertable = false, updatable = false)
  private Long materialId;

  @Column(name = "ID", insertable = false, updatable = false)
  private Long tagId;

  @Id
  @Column(name = "IDX", insertable = false, updatable = false)
  private Long index;

  @OneToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "ID", referencedColumnName = "ID")
  private Tag tag;
}
