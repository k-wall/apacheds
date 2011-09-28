/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.core.prefs;


/**
 * A {@link RuntimeException} that is thrown when accessing
 * {@link ServerSystemPreferences} failed.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 437012 $, $Date: 2006-08-26 01:25:30 +0200 (Sam, 26 aoû 2006) $
 */
public class ServerSystemPreferenceException extends RuntimeException
{
    private static final long serialVersionUID = -2042269063779317751L;


    public ServerSystemPreferenceException()
    {
        super();
    }


    public ServerSystemPreferenceException(String message)
    {
        super( message );
    }


    public ServerSystemPreferenceException(String message, Throwable cause)
    {
        super( message, cause );
    }


    public ServerSystemPreferenceException(Throwable cause)
    {
        super( cause );
    }
}
