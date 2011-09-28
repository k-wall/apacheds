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
package org.apache.directory.server.kerberos.sam;


import java.io.IOException;

import javax.security.auth.kerberos.KerberosKey;

import org.apache.directory.server.kerberos.shared.crypto.encryption.CipherTextHandler;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.crypto.encryption.KeyUsage;
import org.apache.directory.server.kerberos.shared.exceptions.KerberosException;
import org.apache.directory.server.kerberos.shared.io.decoder.EncryptedDataDecoder;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptedData;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptedTimeStamp;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.messages.value.KerberosTime;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 563062 $, $Date: 2007-08-06 10:21:20 +0200 (Lun, 06 aoû 2007) $
 */
public class TimestampChecker implements KeyIntegrityChecker
{
    private static final long FIVE_MINUTES = 300000;
    private static final CipherTextHandler cipherTextHandler = new CipherTextHandler();


    public boolean checkKeyIntegrity( byte[] encryptedData, KerberosKey kerberosKey )
    {
        EncryptionType keyType = EncryptionType.getTypeByOrdinal( kerberosKey.getKeyType() );
        EncryptionKey key = new EncryptionKey( keyType, kerberosKey.getEncoded() );

        try
        {
            /*
             * Since the pre-auth value is of type PA-ENC-TIMESTAMP, it should be a valid
             * ASN.1 PA-ENC-TS-ENC structure, so we can decode it into EncryptedData.
             */
            EncryptedData sadValue = EncryptedDataDecoder.decode( encryptedData );

            /*
             * Decrypt the EncryptedData structure to get the PA-ENC-TS-ENC.  Decode the
             * decrypted timestamp into our timestamp object.
             */
            EncryptedTimeStamp timestamp = ( EncryptedTimeStamp ) cipherTextHandler.unseal( EncryptedTimeStamp.class,
                key, sadValue, KeyUsage.NUMBER1 );

            /*
             * Since we got here we must have a valid timestamp structure that we can
             * validate to be within a five minute skew.
             */
            KerberosTime time = timestamp.getTimeStamp();

            if ( time.isInClockSkew( FIVE_MINUTES ) )
            {
                return true;
            }
        }
        catch ( IOException ioe )
        {
            return false;
        }
        catch ( KerberosException ke )
        {
            return false;
        }
        catch ( ClassCastException cce )
        {
            return false;
        }

        return false;
    }
}
