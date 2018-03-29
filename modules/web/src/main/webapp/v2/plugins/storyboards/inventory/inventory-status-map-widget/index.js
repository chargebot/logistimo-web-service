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

/**
 * Created by yuvaraj on 10/11/17.
 */
angular.module('logistimo.storyboard.inventoryStatusMapWidget', [])
    .config(function (widgetsRepositoryProvider) {
        widgetsRepositoryProvider.addWidget({
            id: "inventoryStatusMapWidget",
            name: "inventory.status.map",
            templateUrl: "plugins/storyboards/inventory/inventory-status-map-widget/inventory-status-map-widget.html",
            editTemplateUrl: "plugins/storyboards/inventory/edit-template.html",
            templateFilters: [
                {
                    nameKey: 'inventory.status',
                    type: 'mapType'
                },
                {
                    nameKey: 'material.upper',
                    type: 'material'
                },
                {
                    nameKey: 'filter.material.tag',
                    type: 'materialTag'
                },
                {
                    nameKey: 'include.entity.tag',
                    type: 'entityTag'
                },
                {
                    nameKey: 'exclude.entity.tag',
                    type: 'exEntityTag'
                },
                {
                    nameKey: 'date',
                    type: 'date'
                },
                {
                    nameKey: 'period.since',
                    type: 'period'
                }
            ],
            defaultHeight: 4,
            defaultWidth: 5
        });
    })
    .controller('inventoryStatusMapWidgetController',
    ['$scope', '$timeout', 'dashboardService', 'domainCfgService', 'INVENTORY', '$sce',
        function ($scope, $timeout, dashboardService, domainCfgService, INVENTORY, $sce) {
            var filter = angular.copy($scope.widget.conf);
            var invPieColors, invPieOrder, mapRange, mapColors, level='', mapName= '',barColor,filterText = '';
            var fDate = (checkNotNullEmpty(filter.date) ? formatDate(filter.date) : undefined);
            $scope.showChart = false;
            $scope.wloading = true;
            $scope.showError = false;
            domainCfgService.getSystemDashboardConfig().then(function (data) {
                var domainConfig = angular.fromJson(data.data);
                mapColors = domainConfig.mc;
                mapRange = domainConfig.mr;
                mapRange['avlbl'] = mapRange['n'];
                mapColors['avlbl'] = mapColors['n'];
                $scope.mr = mapRange;
                $scope.mc = mapColors;
                invPieColors = domainConfig.pie.ic;
                invPieOrder = domainConfig.pie.io;
                $scope.mapEvent = invPieOrder[0];
                $scope.init();
            });

            $scope.init = function () {
                domainCfgService.getMapLocationMapping().then(function (data) {
                    if (!checkNullEmptyObject(data.data)) {
                        $scope.locationMapping = angular.fromJson(data.data);
                        setFilters();
                        loadLocationMap();
                    }
                })
            };

            function setFilters() {
                if (checkNotNullEmpty(filter.period)) {
                    var p = filter.period;
                    if (p == '0' || p == '1' || p == '2' || p == '3' || p == '7' || p == '30') {
                        $scope.period = p;
                    }
                } else {
                    $scope.period = "0";
                }

                if (checkNotNullEmpty(filter.mapType)) {
                    var mapType = filter.mapType;
                    $scope.mapType = mapType;
                    if ($scope.mapType == 0) {
                        $scope.mapEvent = invPieOrder[0];
                        mapName = "Availability";
                        filterText = " - Normal";
                    } else if ($scope.mapType == 1){
                        $scope.mapEvent = invPieOrder[1];
                        mapName = "Stock outs";
                        filterText = " ";
                    } else if ($scope.mapType == 2){
                        $scope.mapEvent = invPieOrder[2];
                        mapName = "Availability";
                        filterText = " - Minimum";
                    } else if ($scope.mapType == 3){
                        $scope.mapEvent = invPieOrder[3];
                        mapName = "Availability";
                        filterText = " - Maximum";
                    } else {
                        $scope.mapEvent = 'avlbl';
                        mapName = "Availability";
                        filterText = " ";
                    }
                } else {
                    $scope.mapType = "0";
                }

                if (checkNotNullEmpty(filter.materialTag)) {
                    $scope.exFilter = constructModel(filter.materialTag);
                    $scope.exType = 'mTag';
                } else if (checkNotNullEmpty(filter.material)) {
                    $scope.exFilter = filter.material.id;
                    $scope.exType = 'mId';
                }
            }

            function loadLocationMap() {
                dashboardService.get(undefined, undefined, $scope.exFilter, $scope.exType, $scope.period, undefined,
                    undefined, constructModel(filter.entityTag), fDate, constructModel(filter.exEntityTag),
                    false).then(function (data) {
                    if(!checkNullEmptyObject(data.data)) {
                        $scope.dashboardView = data.data;
                        if ($scope.mapEvent == 'avlbl') {
                            $scope.dashboardView.inv['avlbl'] = getAvailable($scope.dashboardView.inv);
                        }
                        var linkText;
                        if ($scope.dashboardView.mLev == "country") {
                            linkText = $scope.locationMapping.data[$scope.dashboardView.mTy].name;
                            $scope.mapType = linkText;
                        } else if ($scope.dashboardView.mLev == "state") {
                            $scope.mapType = $scope.dashboardView.mTy;
                        }
                        constructMapData($scope.mapEvent, true, $scope, INVENTORY, $sce, mapRange, mapColors,
                            invPieOrder, $timeout, barColor);
                        setTitle();
                        setWidgetData();
                    }else{
                        $scope.noDataToRender();
                    }
                    }).catch(function error(msg) {
                        showError(msg, $scope);
                    }).finally(function () {
                        $scope.loading = false;
                        $scope.wloading = false;
                    });
            };

            function setTitle() {
                if ($scope.dashboardView.mLev == "country") {
                    level = "state";
                } else if ($scope.dashboardView.mLev == "state") {
                    level = "district";
                }
                $scope.mapTitle = mapName + " by " + level + filterText;
            }

            function setWidgetData() {
                $scope.inventoryStatusMapWidget = {
                    wId: $scope.widget.id,
                    cType: $scope.mapType,
                    copt: $scope.mapOpt,
                    cdata: $scope.mapData,
                    computedWidth: '100%',
                    computedHeight: parseInt($scope.widget.computedHeight, 10) - 30,
                    colorRange: $scope.mapRange,
                    markers: $scope.markers
                };
                $scope.wloading = false;
                $scope.showChart = true;
            }
        }]);

logistimoApp.requires.push('logistimo.storyboard.inventoryStatusMapWidget');
