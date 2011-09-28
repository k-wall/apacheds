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


import java.io.IOException;
import java.util.Enumeration;

import org.apache.directory.server.kerberos.shared.messages.Encodable;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptedTimeStamp;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptedTimeStampModifier;
import org.apache.directory.shared.asn1.der.ASN1InputStream;
import org.apache.directory.shared.asn1.der.DEREncodable;
import org.apache.directory.shared.asn1.der.DERGeneralizedTime;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * padata-type     ::= PA-ENC-TIMESTAMP
 * padata-value    ::= EncryptedData -- PA-ENC-TS-ENC
 * 
 * PA-ENC-TS-ENC   ::= SEQUENCE {
 *         patimestamp[0]               KerberosTime, -- client's time
 *         pausec[1]                    INTEGER OPTIONAL
 * }
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 502338 $, $Date: 2007-02-01 20:59:43 +0100 (Jeu, 01 fév 2007) $
 */
public class EncryptedTimestampDecoder implements Decoder, DecoderFactory
{
    public Decoder getDecoder()
    {
        return new EncryptedTimestampDecoder();
    }


    public Encodable decode( byte[] encodedEncryptedTimestamp ) throws IOException
    {
        ASN1InputStream ais = new ASN1InputStream( encodedEncryptedTimestamp );

        DERSequence sequence = ( DERSequence ) ais.readObject();

        return decodeEncryptedTimestamp( sequence );
    }


    protected EncryptedTimeStamp decodeEncryptedTimestamp( DERSequence sequence )
    {
        EncryptedTimeStampModifier modifier = new EncryptedTimeStampModifier();

        for ( Enumeration e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERTaggedObject object = ( DERTaggedObject ) e.nextElement();
            int tag = object.getTagNo();
            DEREncodable derObject = object.getObject();

            switch ( tag )
            {
                case 0:
                    DERGeneralizedTime tag0 = ( DERGeneralizedTime ) derObject;
                    modifier.setKerberosTime( KerberosTimeDecoder.decode( tag0 ) );
                    break;
                case 1:
                    DERInteger tag1 = ( DERInteger ) derObject;
                    modifier.setMicroSecond( tag1.intValue() );
                    break;
            }
        }

        return modifier.getEncryptedTimestamp();
    }
}
