package io.undertow.servlet.handlers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;

import javax.annotation.Resource;
import javax.servlet.Servlet;
import javax.servlet.sip.SipFactory;
import javax.sip.CreateListeningPointResult;

import org.mobicents.servlet.as8.Connector;
import org.mobicents.servlet.as8.SipConnectorService;
import org.mobicents.servlet.sip.core.MobicentsSipServlet;
import org.mobicents.servlet.sip.core.SipApplicationDispatcher;
import org.mobicents.servlet.sip.undertow.SipServletImpl;
import org.mobicents.servlet.sip.undertow.SipStandardService;
import org.mobicents.servlet.sip.undertow.UndertowSipContextDeployment;
import org.xnio.channels.MulticastMessageChannel;

import gov.nist.javax.sip.stack.UndertowMessageProcessor;
import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.udp.UdpHandler;
import io.undertow.server.protocol.udp.UdpMessage;

public class ConvergedApplicationHandler extends UdpHandler implements HttpHandler {
    private UndertowMessageProcessor messageProcessor;

    private HttpHandler httpHandler;
    private UndertowSipContextDeployment deployment;

    @Override
    public void handleRequest(UdpMessage message) {

        DatagramPacket packet = new DatagramPacket(message.getBufferedData().array(),
                message.getBufferedData().array().length);
        InetSocketAddress sourceAddress = (InetSocketAddress) message.getAddressBuffer().getSourceAddress();
        packet.setAddress(sourceAddress.getAddress());
        packet.setSocketAddress(sourceAddress);

        messageProcessor.addNewMessage(packet);
    }

    @Override
    public void addChannel(MulticastMessageChannel channel) {
        super.addChannel(channel);
        this.init();
    }

    private void init() {
        try {
            SipConnectorService connectorService = new SipConnectorService("udp", "sip", true, "127.0.0.1", 5080,
                    false, null, -1);
            connectorService.start((InetSocketAddress) super.channel.getLocalAddress(), "udp");

            Connector connector = connectorService.getValue();
            connector.getProtocolHandler().init();

            SipStandardService standardService = new SipStandardService();
            standardService.setDarConfigurationFileLocation("mobicents-dar.properties");
            standardService.initialize();
            standardService.addConnector(connector);

            SipApplicationDispatcher dispatcher = (SipApplicationDispatcher) connector.getProtocolHandler()
                    .getAttribute(SipApplicationDispatcher.class.getSimpleName());

            // TODO, több deployment is lehet!!!
            deployment.setApplicationDispatcher(dispatcher);
            deployment.start();

            standardService.start();
            CreateListeningPointResult result = connector.getProtocolHandler().start(this);
            messageProcessor = (UndertowMessageProcessor) result.getMessageProcessor();

            // do some annotation processor test:
            for (MobicentsSipServlet servlet : ((UndertowSipContextDeployment) deployment).getChildrenMap().values()) {
                if (servlet instanceof SipServletImpl) {
                    Servlet sipServlet = ((SipServletImpl) servlet).getServlet().getInstance();
                    this.setSipFactoryResource(sipServlet);
                }
            }
            //looking for httpservlets:
            for(ServletHandler servletHandler : deployment.getServlets().getServletHandlers().values()){
                Servlet servlet = servletHandler.getManagedServlet().getServlet().getInstance();
                this.setSipFactoryResource(servlet);
            }
            

        } catch (Exception ex) {
            UndertowLogger.REQUEST_LOGGER.error(ex.getMessage(), ex);
        } finally {
            // TODO
        }
    }
    
    private void setSipFactoryResource(Servlet servlet)
    {
        for (Field field : servlet.getClass().getDeclaredFields()) {
            Annotation ann = field.getAnnotation(javax.annotation.Resource.class);
            if (ann instanceof Resource && SipFactory.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    field.set(servlet, ((UndertowSipContextDeployment) deployment).getSipFactoryFacade());
                } catch (IllegalArgumentException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } finally {
                    field.setAccessible(false);
                }
            }
        }
    }
    
    
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    public void setHttpHandler(HttpHandler httpHandler) {
        this.httpHandler = httpHandler;
    }

    public UndertowSipContextDeployment getDeployment() {
        return deployment;
    }

    public void setDeployment(UndertowSipContextDeployment deployment) {
        this.deployment = deployment;
    }

    @Override
    public void handleRequest(HttpServerExchange arg0) throws Exception {
        // TODO Auto-generated method stub

    }

}
