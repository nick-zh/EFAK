/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartloli.kafka.eagle.web.quartz.shard.task.sub;

import org.smartloli.kafka.eagle.common.protocol.KpiInfo;
import org.smartloli.kafka.eagle.common.protocol.ZkClusterInfo;
import org.smartloli.kafka.eagle.common.util.*;
import org.smartloli.kafka.eagle.common.util.KConstants.MBean;
import org.smartloli.kafka.eagle.core.factory.KafkaFactory;
import org.smartloli.kafka.eagle.core.factory.KafkaService;
import org.smartloli.kafka.eagle.core.factory.Mx4jFactory;
import org.smartloli.kafka.eagle.core.factory.Mx4jService;
import org.smartloli.kafka.eagle.web.controller.StartupListener;
import org.smartloli.kafka.eagle.web.service.impl.MetricsServiceImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Collect zookeeper cluster dataset.
 *
 * @author smartloli.
 * <p>
 * Created by Dec 09, 2021
 */
public class ZookeeperClusterSubTask extends Thread {

    private static final String zk_packets_received = "zk_packets_received";
    private static final String zk_packets_sent = "zk_packets_sent";
    private static final String zk_num_alive_connections = "zk_num_alive_connections";
    private static final String zk_outstanding_requests = "zk_outstanding_requests";
    private static final String[] zk_kpis = new String[]{zk_packets_received, zk_packets_sent, zk_num_alive_connections, zk_outstanding_requests};

    private static final String[] broker_kpis = new String[]{KConstants.MBean.MESSAGEIN, MBean.BYTEIN, MBean.BYTEOUT, MBean.BYTESREJECTED, MBean.FAILEDFETCHREQUEST, MBean.FAILEDPRODUCEREQUEST, MBean.TOTALFETCHREQUESTSPERSEC, MBean.TOTALPRODUCEREQUESTSPERSEC, MBean.REPLICATIONBYTESINPERSEC, MBean.REPLICATIONBYTESOUTPERSEC, MBean.PRODUCEMESSAGECONVERSIONS,
            KConstants.MBean.OSTOTALMEMORY, MBean.OSFREEMEMORY, MBean.CPUUSED};
    private static final String[] BROKER_KPIS_OFFLINE = new String[]{MBean.MESSAGEIN, MBean.BYTEIN, MBean.BYTEOUT, MBean.BYTESREJECTED, MBean.FAILEDFETCHREQUEST, MBean.FAILEDPRODUCEREQUEST, MBean.TOTALFETCHREQUESTSPERSEC, MBean.TOTALPRODUCEREQUESTSPERSEC, MBean.REPLICATIONBYTESINPERSEC, MBean.REPLICATIONBYTESOUTPERSEC, MBean.PRODUCEMESSAGECONVERSIONS};

    /**
     * Kafka service interface.
     */
    private KafkaService kafkaService = new KafkaFactory().create();

    /**
     * Mx4j service interface.
     */
    private Mx4jService mx4jService = new Mx4jFactory().create();

    @Override
    public void run() {
        try {
            if (SystemConfigUtils.getBooleanProperty("efak.metrics.charts")) {
                String[] clusterAliass = SystemConfigUtils.getPropertyArray("efak.zk.cluster.alias", ",");
                for (String clusterAlias : clusterAliass) {
                    this.zkCluster(clusterAlias);
                }
            }
        } catch (Exception e) {
            LoggerUtils.print(this.getClass()).error("Get zookeeper cluster metrics has error, msg is ", e);
        }
    }

    private void zkCluster(String clusterAlias) {
        List<KpiInfo> list = new ArrayList<>();
        String zkList = SystemConfigUtils.getProperty(clusterAlias + ".zk.list");
        String[] zks = zkList.split(",");
        for (String kpi : zk_kpis) {
            KpiInfo kpiInfo = new KpiInfo();
            kpiInfo.setCluster(clusterAlias);
            kpiInfo.setTm(CalendarUtils.getCustomDate("yyyyMMdd"));
            kpiInfo.setTimespan(CalendarUtils.getTimeSpan());
            kpiInfo.setKey(kpi);
            String broker = "";
            for (String zk : zks) {
                String ip = zk.split(":")[0];
                String port = zk.split(":")[1];
                if (port.contains("/")) {
                    port = port.split("/")[0];
                }
                broker += ip + ",";
                try {
                    ZkClusterInfo zkInfo = ZKMetricsUtils.zkClusterMntrInfo(ip, Integer.parseInt(port));
                    this.zkAssembly(zkInfo, kpi, kpiInfo);
                } catch (Exception ex) {
                    LoggerUtils.print(this.getClass()).error("Transcation string[" + port + "] to int has error, msg is ", ex);
                }
            }
            kpiInfo.setBroker(broker.length() == 0 ? "unkowns" : broker.substring(0, broker.length() - 1));
            kpiInfo.setType(KConstants.CollectorType.ZK);
            list.add(kpiInfo);
        }

        MetricsServiceImpl metrics = StartupListener.getBean("metricsServiceImpl", MetricsServiceImpl.class);
        try {
            metrics.insert(list);
        } catch (Exception e) {
            LoggerUtils.print(this.getClass()).error("Collector zookeeper data has error, msg is ", e);
        }
    }

    private void zkAssembly(ZkClusterInfo zkInfo, String type, KpiInfo kpiInfo) {
        switch (type) {
            case zk_packets_received:
                kpiInfo.setValue(Long.parseLong(StrUtils.isNull(kpiInfo.getValue()) == true ? "0" : kpiInfo.getValue()) + Long.parseLong(StrUtils.isNull(zkInfo.getZkPacketsReceived()) == true ? "0" : zkInfo.getZkPacketsReceived()) + "");
                break;
            case zk_packets_sent:
                kpiInfo.setValue(Long.parseLong(StrUtils.isNull(kpiInfo.getValue()) == true ? "0" : kpiInfo.getValue()) + Long.parseLong(StrUtils.isNull(zkInfo.getZkPacketsSent()) == true ? "0" : zkInfo.getZkPacketsSent()) + "");
                break;
            case zk_num_alive_connections:
                kpiInfo.setValue(Long.parseLong(StrUtils.isNull(kpiInfo.getValue()) == true ? "0" : kpiInfo.getValue()) + Long.parseLong(StrUtils.isNull(zkInfo.getZkNumAliveConnections()) == true ? "0" : zkInfo.getZkNumAliveConnections()) + "");
                break;
            case zk_outstanding_requests:
                kpiInfo.setValue(Long.parseLong(StrUtils.isNull(kpiInfo.getValue()) == true ? "0" : kpiInfo.getValue()) + Long.parseLong(StrUtils.isNull(zkInfo.getZkOutstandingRequests()) == true ? "0" : zkInfo.getZkOutstandingRequests()) + "");
                break;
            default:
                break;
        }
    }
}
