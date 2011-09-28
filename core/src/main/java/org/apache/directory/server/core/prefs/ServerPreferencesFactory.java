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


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.NotImplementedException;

import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;


/**
 * A preferences factory implementation.  Currently the userRoot() preferences
 * are not available and will throw NotImplementedExceptions.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 901735 $
 */
public class ServerPreferencesFactory implements PreferencesFactory
{
    private final DirectoryService directoryService;


    public ServerPreferencesFactory( DirectoryService directoryService )
    {
        this.directoryService = directoryService;
    }


    public Preferences systemRoot()
    {
        return new ServerSystemPreferences( directoryService );
    }


    public Preferences userRoot()
    {
        throw new NotImplementedException( I18n.err( I18n.ERR_269 ) );
    }
}
