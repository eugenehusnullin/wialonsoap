package wialonips;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;

@Service
public class SocketStarter {
	private static final Logger logger = LoggerFactory.getLogger(SocketStarter.class);

	@Value("#{mainSettings['in.host']}")
	private String host;
	@Value("#{mainSettings['in.port']}")
	private int port;

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	private ServerBootstrap serverBootstrap;
	private Channel socketChannel;

	private ExecutorService executor;

	@PostConstruct
	public void run() throws InterruptedException {
		executor = Executors.newCachedThreadPool();

		bossGroup = new NioEventLoopGroup();
		workerGroup = new NioEventLoopGroup();

		serverBootstrap = new ServerBootstrap();
		serverBootstrap.group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(new MessageDecoder(executor));
					}
				})
				.option(ChannelOption.SO_BACKLOG, 128)
				.option(ChannelOption.SO_LINGER, 0)
				// .childOption(ChannelOption.SO_KEEPALIVE, true)
				.childOption(ChannelOption.SO_LINGER, 0);

		ChannelFuture channelFuture = serverBootstrap.bind(host, port).sync();
		socketChannel = channelFuture.channel();

		if (channelFuture.isSuccess()) {
			logger.info("Socket started, on host - " + host + ", port - " + port + ".");
		} else {
			if (channelFuture.isCancelled()) {
				logger.error("Socket did't opened, it was cacelled.");
			} else {
				logger.error("Socket did't opened due error ocured.", channelFuture.cause());
			}
		}
	}

	@PreDestroy
	public void stop() throws InterruptedException {
		// socketchannel
		if (socketChannel != null && socketChannel.isOpen()) {
			ChannelFuture closeFuture = socketChannel.close();
			closeFuture.sync();

			@SuppressWarnings("rawtypes")
			Future fb = bossGroup.shutdownGracefully();
			@SuppressWarnings("rawtypes")
			Future fw = workerGroup.shutdownGracefully();
			try {
				fb.await(1000);
				fw.await(1000);
			} catch (InterruptedException ignore) {
			}
		}
		logger.info("Socket stoped.");

		// executor
		try {
			System.out.println("attempt to shutdown executor");
			executor.shutdown();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			System.err.println("tasks interrupted");
		} finally {
			if (!executor.isTerminated()) {
				System.err.println("cancel non-finished tasks");
			}
			executor.shutdownNow();
			System.out.println("shutdown finished");
		}
	}
}
