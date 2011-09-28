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
package org.apache.directory.server.ldap.handlers.ssl;


import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;


/**
 * An {@link X509TrustManager} for LDAP server.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev:687105 $, $Date:2008-08-19 19:40:48 +0200 (Tue, 19 Aug 2008) $
 */
public class ServerX509TrustManager implements X509TrustManager
{
    public ServerX509TrustManager()
    {
    }


    public void checkClientTrusted( X509Certificate[] arg0, String arg1 ) throws CertificateException
    {
        // We don't check clients at all right now.
        // XXX: Do we need a client-side certificates?
    }


    public void checkServerTrusted( X509Certificate[] arg0, String arg1 ) throws CertificateException
    {
        // It is server-side trust manager, so we don't need to check the server itself.
    }


    public X509Certificate[] getAcceptedIssuers()
    {
        return null;
    }
}
