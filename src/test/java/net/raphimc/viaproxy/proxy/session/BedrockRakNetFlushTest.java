package net.raphimc.viaproxy.proxy.session;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import org.cloudburstmc.netty.channel.raknet.RakChannel;
import org.cloudburstmc.netty.channel.raknet.RakReliability;
import org.cloudburstmc.netty.channel.raknet.config.DefaultRakSessionConfig;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelConfig;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.channel.raknet.packet.EncapsulatedPacket;
import org.cloudburstmc.netty.channel.raknet.packet.RakDatagramPacket;
import org.cloudburstmc.netty.channel.raknet.packet.RakMessage;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodecCompatible;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BedrockRakNetFlushTest {
    @Test void legacyIntervalRetainsBatchingAndReliableOrder() throws Exception { verifyBatch(10); }
    @Test void shorterIntervalRetainsBatchingAndReliableOrder() throws Exception { verifyBatch(5); }

    private void verifyBatch(int interval) throws Exception {
        MemoryChannel channel = new MemoryChannel(interval);
        channel.freezeTime();
        channel.pipeline().addLast(RakSessionCodecCompatible.NAME, new RakSessionCodecCompatible(channel));
        channel.pipeline().fireChannelActive();
        channel.runScheduledPendingTasks();
        assertNull(channel.readOutbound());
        // RakNet的同毫秒维护保护使用真实墙钟，测试调度时钟单独冻结。
        Thread.sleep(2);
        for (int index = 0; index < 3; index++) {
            assertTrue(channel.writeAndFlush(new RakMessage(Unpooled.buffer(2).writeByte(0xfe).writeByte(index))).isSuccess());
        }
        assertTrue(channel.config().isAutoFlush());
        assertNull(channel.readOutbound());
        channel.advanceTimeBy(interval - 1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertNull(channel.readOutbound());
        channel.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        List<Integer> observed = new ArrayList<>();
        RakDatagramPacket datagram;
        while ((datagram = channel.readOutbound()) != null) {
            try {
                for (EncapsulatedPacket packet : datagram.getPackets()) {
                    assertEquals(RakReliability.RELIABLE_ORDERED, packet.getReliability());
                    assertEquals(0, packet.getOrderingChannel());
                    assertEquals(observed.size(), packet.getOrderingIndex());
                    observed.add((int) packet.getBuffer().getUnsignedByte(1));
                }
            } finally {
                datagram.release();
            }
        }
        assertEquals(List.of(0, 1, 2), observed);
        channel.finishAndReleaseAll();
        channel.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertNull(channel.readOutbound());
    }

    private static final class MemoryChannel extends EmbeddedChannel implements RakChannel {
        private final RakChannelConfig rakConfig;
        MemoryChannel(int interval) throws Exception {
            super(false, false);
            rakConfig = new DefaultRakSessionConfig(this);
            assertTrue(rakConfig.setOption(RakChannelOption.RAK_FLUSH_INTERVAL, interval));
            register();
        }
        @Override public RakChannelConfig config() { return rakConfig; }
        @Override public Channel parent() { return this; }
        @Override public ChannelPipeline rakPipeline() { return pipeline(); }
        @Override protected SocketAddress remoteAddress0() { return new InetSocketAddress("127.0.0.1", 19152); }
    }
}
