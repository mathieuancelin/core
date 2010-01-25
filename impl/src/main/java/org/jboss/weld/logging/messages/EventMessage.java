/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.logging.messages;

import org.jboss.weld.logging.MessageId;

import ch.qos.cal10n.BaseName;
import ch.qos.cal10n.Locale;
import ch.qos.cal10n.LocaleData;

@BaseName("org.jboss.weld.messages.event")
@LocaleData({
   @Locale("en")
})
/**
 * Log messages for events
 * 
 * Message ids: 000400 - 000499
 */
public enum EventMessage
{

   @MessageId("000400") ASYNC_FIRE,
   @MessageId("000401") ASYNC_OBSERVER_FAILURE,
   @MessageId("000402") ASYNC_TX_FIRE,
   @MessageId("000403") PROXY_REQUIRED,
   @MessageId("000404") INVALID_SCOPED_CONDITIONAL_OBSERVER,
   @MessageId("000405") MULTIPLE_EVENT_PARAMETERS,
   @MessageId("000406") INVALID_DISPOSES_PARAMETER,
   @MessageId("000407") INVALID_PRODUCER,
   @MessageId("000408") INVALID_INITIALIZER;
   
}
