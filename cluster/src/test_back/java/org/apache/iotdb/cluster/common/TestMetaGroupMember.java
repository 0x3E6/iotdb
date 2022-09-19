/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.cluster.common;

import org.apache.iotdb.cluster.client.ClientManager;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.impl.PlanBasedStateMachine;
import org.apache.iotdb.cluster.log.manage.MetaSingleSnapshotLogManager;
import org.apache.iotdb.cluster.partition.PartitionGroup;
import org.apache.iotdb.cluster.server.member.MetaGroupMember;

public class TestMetaGroupMember extends MetaGroupMember {

  public TestMetaGroupMember() {
    super(new PlanBasedStateMachine());

    MetaSingleSnapshotLogManager manager =
        new MetaSingleSnapshotLogManager(this);
    setLogManager(manager);

    PartitionGroup group = new PartitionGroup();
    thisNode = TestUtils.getNode(0);
    for (int i = 0; i < 10; i++) {
      group.add(TestUtils.getNode(i));
    }
    setAllNodes(group);

    this.clientManager =
        new ClientManager(
            ClusterDescriptor.getInstance().getConfig().isUseAsyncServer(),
            ClientManager.Type.MetaGroupClient);
  }
}
