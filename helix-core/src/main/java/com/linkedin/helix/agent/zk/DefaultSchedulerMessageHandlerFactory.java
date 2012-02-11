package com.linkedin.helix.agent.zk;

import java.io.StringReader;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import com.linkedin.helix.ClusterManager;
import com.linkedin.helix.ClusterManagerException;
import com.linkedin.helix.Criteria;
import com.linkedin.helix.NotificationContext;
import com.linkedin.helix.ZNRecord;
import com.linkedin.helix.messaging.AsyncCallback;
import com.linkedin.helix.messaging.handling.CMTaskResult;
import com.linkedin.helix.messaging.handling.MessageHandler;
import com.linkedin.helix.messaging.handling.MessageHandlerFactory;
import com.linkedin.helix.model.Message;
import com.linkedin.helix.model.Message.MessageType;
import com.linkedin.helix.util.StatusUpdateUtil;
/*
 * TODO: The current implementation is temporary for backup handler testing only and it does not 
 * do any throttling. 
 * 
 * **/
public class DefaultSchedulerMessageHandlerFactory implements
    MessageHandlerFactory
{
  public static class SchedulerAsyncCallback extends AsyncCallback
  {
    StatusUpdateUtil _statusUpdateUtil = new StatusUpdateUtil();
    Message _originalMessage;
    ClusterManager _manager;
    public SchedulerAsyncCallback(Message originalMessage, ClusterManager manager)
    {
      _originalMessage = originalMessage;
      _manager = manager;
    }
    
    @Override
    public void onTimeOut()
    {
      _statusUpdateUtil.logError(_originalMessage, SchedulerAsyncCallback.class, "Task timeout", _manager.getDataAccessor());
      
    }

    @Override
    public void onReplyMessage(Message message)
    {
      _statusUpdateUtil.logInfo(_originalMessage, SchedulerAsyncCallback.class, 
          "Message "+message.getMsgSrc()+" completed", _manager.getDataAccessor());
      
    }
    
  }
  
  private static Logger _logger = Logger.getLogger(DefaultSchedulerMessageHandlerFactory.class);
  ClusterManager _manager;
  public DefaultSchedulerMessageHandlerFactory(ClusterManager manager)
  {
    _manager = manager;
  }
  
  @Override
  public MessageHandler createHandler(Message message,
      NotificationContext context)
  {
    String type = message.getMsgType();
    
    if(!type.equals(getMessageType()))
    {
      throw new ClusterManagerException("Unexpected msg type for message "+message.getMsgId()
          +" type:" + message.getMsgType());
    }
    
    return new DefaultSchedulerMessageHandler(message, context, _manager);
  }

  @Override
  public String getMessageType()
  {
    return MessageType.SCHEDULER_MSG.toString();
  }

  @Override
  public void reset()
  {
  }
  
  public static class DefaultSchedulerMessageHandler extends MessageHandler
  {
    ClusterManager _manager;
    public DefaultSchedulerMessageHandler(Message message,
        NotificationContext context, ClusterManager manager)
    {
      super(message, context);
      _manager = manager;
    }

    @Override
    public CMTaskResult handleMessage() throws InterruptedException
    {
      String type = _message.getMsgType();
      CMTaskResult result = new CMTaskResult();
      if(!type.equals(MessageType.SCHEDULER_MSG.toString()))
      {
        throw new ClusterManagerException("Unexpected msg type for message "+_message.getMsgId()
            +" type:" + _message.getMsgType());
      }
      // Parse timeout value
      int timeOut = -1;
      if(_message.getRecord().getSimpleFields().containsKey("TIMEOUT"))
      {
        try
        {
          timeOut = Integer.parseInt(_message.getRecord().getSimpleFields().get("TIMEOUT"));
        }
        catch(Exception e)
        {}
      }
        
      // Parse the message template
      ZNRecord record = new ZNRecord("templateMessage");
      record.getSimpleFields().putAll(_message.getRecord().getMapField("MessageTemplate"));
      Message messageTemplate = new Message(record);
      
      // Parse the criteria
      StringReader sr = new StringReader(_message.getRecord().getSimpleField("Criteria"));
      ObjectMapper mapper = new ObjectMapper();
      Criteria recipientCriteria;
      try
      {
        recipientCriteria = mapper.readValue(sr, Criteria.class);
      }
      catch (Exception e)
      {
        _logger.error("", e);
        result.setException(e);
        result.setSuccess(false);
        return result;
      }
      
      // Send all messages.
      _manager.getMessagingService().send(recipientCriteria, messageTemplate, new SchedulerAsyncCallback(_message, _manager), timeOut);
      
      result.getTaskResultMap().put("ControllerResult", "msg "+ _message.getMsgId() + " from "+_message.getMsgSrc() + " processed");
      result.setSuccess(true);
      return result;
    }

    @Override
    public void onError(Exception e, ErrorCode code, ErrorType type)
    {
      _logger.error("Message handling pipeline get an exception. MsgId:" + _message.getMsgId(), e);
    }
  }
}
