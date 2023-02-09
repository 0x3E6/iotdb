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

package org.apache.iotdb.cluster.log.logtypes;

import org.apache.iotdb.cluster.log.Log;
import org.apache.iotdb.commons.exception.IllegalPathException;
import org.apache.iotdb.consensus.common.request.IConsensusRequest;
import org.apache.iotdb.db.qp.physical.PhysicalPlan;
import org.apache.iotdb.db.qp.physical.sys.DummyPlan;
import org.apache.iotdb.tsfile.utils.PublicBAOS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static org.apache.iotdb.cluster.log.Log.Types.PHYSICAL_PLAN;

/** RequestLog contains a non-partitioned request like set storage group. */
public class RequestLog extends Log {

  private static final Logger logger = LoggerFactory.getLogger(RequestLog.class);
  private IConsensusRequest request;

  public RequestLog() {}

  public RequestLog(IConsensusRequest request) {
    this.request = request;
  }

  @Override
  public int getDefaultBufferSize() {
    if (request instanceof DummyPlan) {
      int workloadSize =
          ((DummyPlan) request).getWorkload() == null
              ? 0
              : ((DummyPlan) request).getWorkload().length;
      return workloadSize + 512;
    }
    return DEFAULT_BUFFER_SIZE;
  }

  @Override
  public ByteBuffer serialize() {
    PublicBAOS byteArrayOutputStream = new PublicBAOS(getDefaultBufferSize());
    try (DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
      dataOutputStream.writeByte((byte) PHYSICAL_PLAN.ordinal());

      dataOutputStream.writeLong(getCurrLogIndex());
      dataOutputStream.writeLong(getCurrLogTerm());

      ByteBuffer byteBuffer = request.serializeToByteBuffer();
      dataOutputStream.write(
          byteBuffer.array(), byteBuffer.arrayOffset(), byteBuffer.limit() - byteBuffer.position());
    } catch (IOException e) {
      // unreachable
    }

    return ByteBuffer.wrap(byteArrayOutputStream.getBuf(), 0, byteArrayOutputStream.size());
  }

  @Override
  public void serialize(ByteBuffer buffer) {
    buffer.put((byte) PHYSICAL_PLAN.ordinal());
    buffer.putLong(getCurrLogIndex());
    buffer.putLong(getCurrLogTerm());
    buffer.put(request.serializeToByteBuffer());
  }

  @Override
  public void deserialize(ByteBuffer buffer) {
    setCurrLogIndex(buffer.getLong());
    setCurrLogTerm(buffer.getLong());

    try {
      request = PhysicalPlan.Factory.create(buffer);
    } catch (IOException | IllegalPathException e) {
      logger.error(
          "Cannot parse a physical {}:{} plan {}",
          getCurrLogIndex(),
          getCurrLogTerm(),
          buffer.array().length,
          e);
    }
  }

  public IConsensusRequest getRequest() {
    return request;
  }

  public void setRequest(IConsensusRequest request) {
    this.request = request;
  }

  @Override
  public String toString() {
    return request + ",term:" + getCurrLogTerm() + ",index:" + getCurrLogIndex();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    RequestLog that = (RequestLog) o;
    return Objects.equals(request, that.request);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), request);
  }

  @Override
  public long estimateSize() {
    return request.estimateSize();
  }
}
