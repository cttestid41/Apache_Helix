package org.apache.helix.manager.file;


/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.helix.ConfigScope;
import org.apache.helix.HelixAdmin;
import org.apache.helix.PropertyPathConfig;
import org.apache.helix.PropertyType;
import org.apache.helix.ZNRecord;
import org.apache.helix.ConfigScope.ConfigScopeProperty;
import org.apache.helix.model.ExternalView;
import org.apache.helix.model.IdealState;
import org.apache.helix.model.InstanceConfig;
import org.apache.helix.model.StateModelDefinition;
import org.apache.helix.model.IdealState.IdealStateModeProperty;
import org.apache.helix.store.PropertyStoreException;
import org.apache.helix.store.file.FilePropertyStore;
import org.apache.helix.tools.StateModelConfigGenerator;
import org.apache.helix.util.HelixUtil;
import org.apache.log4j.Logger;


@Deprecated
public class FileHelixAdmin implements HelixAdmin
{
  private static Logger                     logger =
                                                       Logger.getLogger(FileHelixAdmin.class);
  private final FilePropertyStore<ZNRecord> _store;

  public FileHelixAdmin(FilePropertyStore<ZNRecord> store)
  {
    _store = store;
  }

  @Override
  public List<String> getClusters()
  {
    throw new UnsupportedOperationException("getClusters() is NOT supported by FileClusterManagementTool");

  }

  @Override
  public List<String> getInstancesInCluster(String clusterName)
  {
    // String path = HelixUtil.getConfigPath(clusterName);
    String path =
        PropertyPathConfig.getPath(PropertyType.CONFIGS,
                                   clusterName,
                                   ConfigScopeProperty.PARTICIPANT.toString());

    List<String> childs = null;
    List<String> instanceNames = new ArrayList<String>();
    try
    {
      childs = _store.getPropertyNames(path);
      for (String child : childs)
      {
        // strip config path from instanceName
        String instanceName = child.substring(child.lastIndexOf('/') + 1);
        instanceNames.add(instanceName);
      }
      return instanceNames;
    }
    catch (PropertyStoreException e)
    {
      logger.error("Fail to getInstancesInCluster, cluster " + clusterName, e);
    }

    return null;
  }

  @Override
  public List<String> getResourcesInCluster(String clusterName)
  {
    // TODO Auto-generated method stub
    // return null;
    throw new UnsupportedOperationException("getResourcesInCluster() is NOT supported by FileClusterManagementTool");

  }

  @Override
  public void addCluster(String clusterName, boolean overwritePrevRecord)
  {
    String path;
    try
    {
      _store.removeNamespace(clusterName);
      _store.createPropertyNamespace(clusterName);
      _store.createPropertyNamespace(HelixUtil.getIdealStatePath(clusterName));

      // CONFIG's
      // _store.createPropertyNamespace(HelixUtil.getConfigPath(clusterName));
      path =
          PropertyPathConfig.getPath(PropertyType.CONFIGS,
                                     clusterName,
                                     ConfigScopeProperty.CLUSTER.toString(),
                                     clusterName);
      _store.setProperty(path, new ZNRecord(clusterName));
      path =
          PropertyPathConfig.getPath(PropertyType.CONFIGS,
                                     clusterName,
                                     ConfigScopeProperty.PARTICIPANT.toString());
      _store.createPropertyNamespace(path);
      path =
          PropertyPathConfig.getPath(PropertyType.CONFIGS,
                                     clusterName,
                                     ConfigScopeProperty.RESOURCE.toString());
      _store.createPropertyNamespace(path);

      // PROPERTY STORE
      path = PropertyPathConfig.getPath(PropertyType.PROPERTYSTORE, clusterName);
      _store.createPropertyNamespace(path);

      _store.createPropertyNamespace(HelixUtil.getLiveInstancesPath(clusterName));
      _store.createPropertyNamespace(HelixUtil.getMemberInstancesPath(clusterName));
      _store.createPropertyNamespace(HelixUtil.getExternalViewPath(clusterName));
      _store.createPropertyNamespace(HelixUtil.getStateModelDefinitionPath(clusterName));

      StateModelConfigGenerator generator = new StateModelConfigGenerator();
      addStateModelDef(clusterName,
                       "MasterSlave",
                       new StateModelDefinition(generator.generateConfigForMasterSlave()));

      // controller
      _store.createPropertyNamespace(HelixUtil.getControllerPath(clusterName));
      path = PropertyPathConfig.getPath(PropertyType.HISTORY, clusterName);
      final ZNRecord emptyHistory = new ZNRecord(PropertyType.HISTORY.toString());
      final List<String> emptyList = new ArrayList<String>();
      emptyHistory.setListField(clusterName, emptyList);
      _store.setProperty(path, emptyHistory);

      path = PropertyPathConfig.getPath(PropertyType.MESSAGES_CONTROLLER, clusterName);
      _store.createPropertyNamespace(path);

      path =
          PropertyPathConfig.getPath(PropertyType.STATUSUPDATES_CONTROLLER, clusterName);
      _store.createPropertyNamespace(path);

      path = PropertyPathConfig.getPath(PropertyType.ERRORS_CONTROLLER, clusterName);
      _store.createPropertyNamespace(path);

    }
    catch (PropertyStoreException e)
    {
      logger.error("Fail to add cluster " + clusterName, e);
    }

  }

  @Override
  public void addResource(String clusterName,
                          String resource,
                          int numResources,
                          String stateModelRef)
  {
    String idealStatePath = HelixUtil.getIdealStatePath(clusterName);
    String resourceIdealStatePath = idealStatePath + "/" + resource;

    // if (_zkClient.exists(dbIdealStatePath))
    // {
    // logger.warn("Skip the operation. DB ideal state directory exists:"
    // + dbIdealStatePath);
    // return;
    // }

    IdealState idealState = new IdealState(resource);
    idealState.setNumPartitions(numResources);
    idealState.setStateModelDefRef(stateModelRef);
    idealState.setReplicas(Integer.toString(0));
    idealState.setIdealStateMode(IdealStateModeProperty.AUTO.toString());
    try
    {
      _store.setProperty(resourceIdealStatePath, idealState.getRecord());
    }
    catch (PropertyStoreException e)
    {
      logger.error("Fail to add resource, cluster:" + clusterName + " resourceName:"
          + resource, e);
    }

  }

  @Override
  public void addResource(String clusterName,
                          String resource,
                          int numResources,
                          String stateModelRef,
                          String idealStateMode)
  {
    throw new UnsupportedOperationException("ideal state mode not supported in file-based cluster manager");
  }

  @Override
  public void addResource(String clusterName,
                          String resource,
                          int numResources,
                          String stateModelRef,
                          String idealStateMode,
                          int bucketSize)
  {
    throw new UnsupportedOperationException("bucketize not supported in file-based cluster manager");
  }

  @Override
  public void addInstance(String clusterName, InstanceConfig config)
  {
    String configsPath =
        PropertyPathConfig.getPath(PropertyType.CONFIGS,
                                   clusterName,
                                   ConfigScopeProperty.PARTICIPANT.toString());
    String nodeId = config.getId();
    String nodeConfigPath = configsPath + "/" + nodeId;

    try
    {
      _store.setProperty(nodeConfigPath, config.getRecord());
      _store.createPropertyNamespace(HelixUtil.getMessagePath(clusterName, nodeId));
      _store.createPropertyNamespace(HelixUtil.getCurrentStateBasePath(clusterName,
                                                                       nodeId));
      _store.createPropertyNamespace(HelixUtil.getErrorsPath(clusterName, nodeId));
      _store.createPropertyNamespace(HelixUtil.getStatusUpdatesPath(clusterName, nodeId));
    }
    catch (Exception e)
    {
      logger.error("Fail to add node, cluster:" + clusterName + "\nexception: " + e);
    }

  }

  @Override
  public void dropInstance(String clusterName, InstanceConfig config)
  {
    String configsPath =
        PropertyPathConfig.getPath(PropertyType.CONFIGS,
                                   clusterName,
                                   ConfigScopeProperty.PARTICIPANT.toString());
    String nodeId = config.getId();
    String nodeConfigPath = configsPath + "/" + nodeId;

    try
    {
      _store.setProperty(nodeConfigPath, config.getRecord());
    }
    catch (Exception e)
    {
      logger.error("Fail to drop node, cluster:" + clusterName, e);
    }
  }

  @Override
  public IdealState getResourceIdealState(String clusterName, String resourceName)
  {
    return new FileDataAccessor(_store, clusterName).getProperty(IdealState.class,
                                                                 PropertyType.IDEALSTATES,
                                                                 resourceName);
  }

  @Override
  public void setResourceIdealState(String clusterName,
                                    String resourceName,
                                    IdealState idealState)
  {
    new FileDataAccessor(_store, clusterName).setProperty(PropertyType.IDEALSTATES,
                                                          idealState,
                                                          resourceName);
  }

  @Override
  public void enableInstance(String clusterName, String instanceName, boolean enabled)
  {
    throw new UnsupportedOperationException("enableInstance() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public void enableCluster(String clusterName, boolean enabled)
  {
    throw new UnsupportedOperationException("enableCluster() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public void addStateModelDef(String clusterName,
                               String stateModelDef,
                               StateModelDefinition stateModel)
  {

    String stateModelDefPath = HelixUtil.getStateModelDefinitionPath(clusterName);
    String stateModelPath = stateModelDefPath + "/" + stateModelDef;

    try
    {
      _store.setProperty(stateModelPath, stateModel.getRecord());
    }
    catch (PropertyStoreException e)
    {
      logger.error("Fail to addStateModelDef, cluster:" + clusterName + " stateModelDef:"
          + stateModelDef, e);
    }

  }

  @Override
  public void dropResource(String clusterName, String resourceName)
  {
    new FileDataAccessor(_store, clusterName).removeProperty(PropertyType.IDEALSTATES,
                                                             resourceName);

  }

  @Override
  public List<String> getStateModelDefs(String clusterName)
  {
    throw new UnsupportedOperationException("getStateModelDefs() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public InstanceConfig getInstanceConfig(String clusterName, String instanceName)
  {
    throw new UnsupportedOperationException("getInstanceConfig() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public StateModelDefinition getStateModelDef(String clusterName, String stateModelName)
  {
    throw new UnsupportedOperationException("getStateModelDef() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public ExternalView getResourceExternalView(String clusterName, String resource)
  {
    throw new UnsupportedOperationException("getResourceExternalView() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public void enablePartition(boolean enabled,
                              String clusterName,
                              String instanceName,
                              String resourceName,
                              List<String> partitionNames)
  {
    throw new UnsupportedOperationException("enablePartition() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public void resetPartition(String clusterName,
                             String instanceName,
                             String resourceName,
                             List<String> partitionNames)
  {
    throw new UnsupportedOperationException("resetPartition() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public void resetInstance(String clusterName, List<String> instanceNames)
  {
    throw new UnsupportedOperationException("resetInstance() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public void resetResource(String clusterName, List<String> resourceNames)
  {
    throw new UnsupportedOperationException("resetResource() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public void addStat(String clusterName, String statName)
  {
    throw new UnsupportedOperationException("addStat() is NOT supported by FileClusterManagementTool");

  }

  @Override
  public void addAlert(String clusterName, String alertName)
  {
    throw new UnsupportedOperationException("addAlert() is NOT supported by FileClusterManagementTool");

  }

  @Override
  public void dropStat(String clusterName, String statName)
  {
    throw new UnsupportedOperationException("dropStat() is NOT supported by FileClusterManagementTool");

  }

  @Override
  public void dropAlert(String clusterName, String alertName)
  {
    throw new UnsupportedOperationException("dropAlert() is NOT supported by FileClusterManagementTool");

  }

  @Override
  public void dropCluster(String clusterName)
  {
    throw new UnsupportedOperationException("dropCluster() is NOT supported by FileClusterManagementTool");
  }

  @Override
  public void addClusterToGrandCluster(String clusterName, String grandCluster)
  {
    throw new UnsupportedOperationException("addCluster(clusterName, overwritePrevRecord, grandCluster) is NOT supported by FileClusterManagementTool");
  }

  @Override
  public void setConfig(ConfigScope scope, Map<String, String> properties)
  {
    throw new UnsupportedOperationException("unsupported operation");
  }

  @Override
  public Map<String, String> getConfig(ConfigScope scope, Set<String> keys)
  {
    throw new UnsupportedOperationException("unsupported operation");
  }

  @Override
  public List<String> getConfigKeys(ConfigScopeProperty scope,
                                    String clusterName,
                                    String... keys)
  {
    throw new UnsupportedOperationException("unsupported operation");
  }

  @Override
  public void removeConfig(ConfigScope scope, Set<String> keys)
  {
    throw new UnsupportedOperationException("unsupported operation");

  }

  @Override
  public void rebalance(String clusterName, String resourceName, int replica)
  {
    throw new UnsupportedOperationException("unsupported operation");
  }

  @Override
  public void addIdealState(String clusterName, String resourceName, String idealStateFile) throws IOException
  {
    throw new UnsupportedOperationException("unsupported operation");
  }

  @Override
  public void addStateModelDef(String clusterName,
                               String stateModelDefName,
                               String stateModelDefFile) throws IOException
  {
    throw new UnsupportedOperationException("unsupported operation");
  }

  @Override
  public void addMessageConstraint(String clusterName,
                                   String constraintId,
                                   Map<String, String> constraints)
  {
    throw new UnsupportedOperationException("unsupported operation");
  }

  @Override
  public void addResource(String clusterName, String resourceName,
      IdealState idealstate)
  {
    throw new UnsupportedOperationException("unsupported operation");
  }
}