package com.di.mesa.job.jstorm.topology;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.drpc.LinearDRPCTopologyBuilder;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Fields;
import com.di.mesa.job.jstorm.blot.MergeRecord;
import com.di.mesa.job.jstorm.blot.PairCount;
import com.di.mesa.job.jstorm.blot.SplitRecord;
import com.di.mesa.job.jstorm.blot.TotalCount;
import com.di.mesa.job.jstorm.configure.SequenceTopologyConfigure;
import com.di.mesa.job.jstorm.spout.SequenceSpout;

import java.util.HashMap;
import java.util.Map;

public class SequenceTopology {

    private final static String TOPOLOGY_SPOUT_PARALLELISM_HINT = "topology_spout_parallelism_hint";
    private final static String TOPOLOGY_BOLT_PARALLELISM_HINT = "topology_bolt_parallelism_hint";

    public static void SetBuilder(TopologyBuilder builder, Map conf) {

        int spout_Parallelism_hint = conf.get(TOPOLOGY_SPOUT_PARALLELISM_HINT) == null ? 1
                : (Integer) conf.get(TOPOLOGY_SPOUT_PARALLELISM_HINT);
        int bolt_Parallelism_hint = conf.get(TOPOLOGY_BOLT_PARALLELISM_HINT) == null ? 2
                : (Integer) conf.get(TOPOLOGY_BOLT_PARALLELISM_HINT);

        builder.setSpout(SequenceTopologyConfigure.SEQUENCE_SPOUT_NAME, new SequenceSpout(), 1);

        boolean isEnableSplit = false;

        if (isEnableSplit == false) {
            builder.setBolt(SequenceTopologyConfigure.TOTAL_BOLT_NAME, new TotalCount(),
                    bolt_Parallelism_hint).shuffleGrouping(SequenceTopologyConfigure.SEQUENCE_SPOUT_NAME);
        } else {
            builder.setBolt(SequenceTopologyConfigure.TOTAL_BOLT_NAME, new TotalCount(),
                    bolt_Parallelism_hint).noneGrouping(SequenceTopologyConfigure.SEQUENCE_SPOUT_NAME);

            builder.setBolt(SequenceTopologyConfigure.SPLIT_BOLT_NAME, new SplitRecord(), 2)
                    .shuffleGrouping(SequenceTopologyConfigure.SEQUENCE_SPOUT_NAME);

            builder.setBolt(SequenceTopologyConfigure.TRADE_BOLT_NAME, new PairCount(), 1)
                    .shuffleGrouping(SequenceTopologyConfigure.SPLIT_BOLT_NAME,
                            SequenceTopologyConfigure.TRADE_STREAM_ID);
            builder.setBolt(SequenceTopologyConfigure.CUSTOMER_BOLT_NAME, new PairCount(), 1)
                    .shuffleGrouping(SequenceTopologyConfigure.SPLIT_BOLT_NAME,
                            SequenceTopologyConfigure.CUSTOMER_STREAM_ID);

            builder.setBolt(SequenceTopologyConfigure.MERGE_BOLT_NAME, new MergeRecord(), 2)
                    .fieldsGrouping(SequenceTopologyConfigure.TRADE_BOLT_NAME, new Fields("ID"))
                    .fieldsGrouping(SequenceTopologyConfigure.CUSTOMER_BOLT_NAME, new Fields("ID"));

            builder.setBolt(SequenceTopologyConfigure.TOTAL_BOLT_NAME, new TotalCount(), 2).noneGrouping(
                    SequenceTopologyConfigure.MERGE_BOLT_NAME);
        }

        conf.put(Config.TOPOLOGY_DEBUG, false);
        Config.setNumAckers(conf, 1);

        conf.put(Config.TOPOLOGY_WORKERS, 20);

    }

    public static void SetLocalTopology() throws InterruptedException {
        TopologyBuilder builder = new TopologyBuilder();
        Map conf = new HashMap();

        SetBuilder(builder, conf);
        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology("SplitMerge", conf, builder.createTopology());
        Thread.sleep(1000000);
        cluster.shutdown();
    }

    public static void SetRemoteTopology(String streamName, Integer spout_parallelism_hint,
                                         Integer bolt_parallelism_hint)
            throws AlreadyAliveException,
            InvalidTopologyException {
        TopologyBuilder builder = new TopologyBuilder();

        Map conf = new HashMap();
        conf.put(TOPOLOGY_SPOUT_PARALLELISM_HINT, spout_parallelism_hint);
        conf.put(TOPOLOGY_BOLT_PARALLELISM_HINT, bolt_parallelism_hint);

        SetBuilder(builder, conf);

        conf.put(Config.STORM_CLUSTER_MODE, "distributed");

        if (streamName.contains("netty")) {
            conf.put(Config.STORM_MESSAGING_TRANSPORT,
                    "com.alibaba.jstorm.message.netty.NettyContext");
        } else {
            conf.put(Config.STORM_MESSAGING_TRANSPORT,
                    "com.alibaba.jstorm.message.zeroMq.MQContext");
        }

        StormSubmitter.submitTopology(streamName, conf, builder.createTopology());

    }

    public static void SetDPRCTopology() throws AlreadyAliveException, InvalidTopologyException {
        LinearDRPCTopologyBuilder builder = new LinearDRPCTopologyBuilder("exclamation");

        builder.addBolt(new TotalCount(), 3);

        Config conf = new Config();

        conf.setNumWorkers(3);
        StormSubmitter.submitTopology("rpc", conf, builder.createRemoteTopology());
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            if (args[0] == "rpc") {
                SetDPRCTopology();
                return;
            } else if (args[0] == "local") {
                SetLocalTopology();
                return;
            }
        }

        String topologyName = "SequenceTest";
        if (args.length > 0) {
            topologyName = args[0];
        }

        //args: 0-topologyName, 1-spoutParallelism, 2-boltParallelism
        Integer spout_parallelism_hint = null;
        Integer bolt_parallelism_hint = null;
        if (args.length > 1) {
            spout_parallelism_hint = Integer.parseInt(args[1]);
            if (args.length > 2) {
                bolt_parallelism_hint = Integer.parseInt(args[2]);
            }
        }
        SetRemoteTopology(topologyName, spout_parallelism_hint, bolt_parallelism_hint);
    }

}
