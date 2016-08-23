package wialonips;

import java.nio.charset.Charset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

public class MessageDecoder extends ChannelHandlerAdapter {
	private static final Logger logger = LoggerFactory.getLogger(MessageDecoder.class);
	private Charset asciiCharset = Charset.forName("ASCII");
	public final static AttributeKey<UUID> AK_UUID = AttributeKey.valueOf("uuid");

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		ByteBuf buf = (ByteBuf) msg;
		byte[] messageBytes = new byte[buf.writerIndex()];
		buf.readBytes(messageBytes);
		String str = new String(messageBytes, asciiCharset);
		logger.debug(str);
	}
	
	@Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		UUID uuid = UUID.randomUUID();
		ctx.channel().attr(AK_UUID).set(uuid);
		
		logger.debug("  channelRegistered - " + uuid.toString());
        super.channelRegistered(ctx);
    }

	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
		UUID uuid = ctx.channel().attr(AK_UUID).get();
		logger.debug("channelUnregistered - " + uuid.toString());
		super.channelUnregistered(ctx);
	}

}
