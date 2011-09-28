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
package org.apache.directory.server.kerberos.shared.io.decoder;


import java.text.ParseException;
import java.util.Date;

import org.apache.directory.server.kerberos.shared.messages.value.KerberosTime;
import org.apache.directory.shared.asn1.der.DERGeneralizedTime;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 502338 $, $Date: 2007-02-01 20:59:43 +0100 (Jeu, 01 fév 2007) $
 */
public class KerberosTimeDecoder
{
    /**
     * KerberosTime ::=   GeneralizedTime
     *             -- Specifying UTC time zone (Z)
     */
    protected static KerberosTime decode( DERGeneralizedTime time )
    {
        Date date = null;

        try
        {
            date = time.getDate();
        }
        catch ( ParseException pe )
        {
            pe.printStackTrace();
        }

        return new KerberosTime( date );
    }
}
