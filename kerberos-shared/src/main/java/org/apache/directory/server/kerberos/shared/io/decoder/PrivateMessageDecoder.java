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

import org.apache.directory.server.kerberos.shared.KerberosMessageType;
import org.apache.directory.server.kerberos.shared.messages.application.PrivateMessage;
import org.apache.directory.shared.asn1.der.ASN1InputStream;
import org.apache.directory.shared.asn1.der.DERApplicationSpecific;
import org.apache.directory.shared.asn1.der.DEREncodable;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 589780 $, $Date: 2007-10-29 19:14:59 +0100 (Lun, 29 oct 2007) $
 */
public class PrivateMessageDecoder
{
    /**
     * Decodes a byte array into a {@link PrivateMessage}.
     *
     * @param encodedPrivateMessage
     * @return The {@link PrivateMessage}.
     * @throws IOException
     */
    public PrivateMessage decode( byte[] encodedPrivateMessage ) throws IOException
    {
        ASN1InputStream ais = new ASN1InputStream( encodedPrivateMessage );

        DERApplicationSpecific app = ( DERApplicationSpecific ) ais.readObject();

        DERSequence privateMessage = ( DERSequence ) app.getObject();

        return decodePrivateMessageSequence( privateMessage );
    }


    private PrivateMessage decodePrivateMessageSequence( DERSequence sequence )
    {
        PrivateMessage message = new PrivateMessage();

        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERTaggedObject object = ( DERTaggedObject ) e.nextElement();
            int tag = object.getTagNo();
            DEREncodable derObject = object.getObject();

            switch ( tag )
            {
                case 0:
                    DERInteger tag0 = ( DERInteger ) derObject;
                    message.setProtocolVersionNumber( tag0.intValue() );
                    break;
                    
                case 1:
                    DERInteger tag1 = ( DERInteger ) derObject;
                    message.setMessageType( KerberosMessageType.getTypeByOrdinal( tag1.intValue() ) );
                    break;
                    
                case 3:
                    DERSequence tag3 = ( DERSequence ) derObject;
                    message.setEncryptedPart( EncryptedDataDecoder.decode( tag3 ) );
                    break;
            }
        }

        return message;
    }
}
