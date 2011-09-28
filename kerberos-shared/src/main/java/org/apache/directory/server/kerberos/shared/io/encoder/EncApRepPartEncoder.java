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
package org.apache.directory.server.kerberos.shared.io.encoder;


import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.directory.server.kerberos.shared.messages.Encodable;
import org.apache.directory.server.kerberos.shared.messages.components.EncApRepPart;
import org.apache.directory.shared.asn1.der.ASN1OutputStream;
import org.apache.directory.shared.asn1.der.DERApplicationSpecific;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 540371 $, $Date: 2007-05-22 02:00:43 +0200 (Mar, 22 mai 2007) $
 */
public class EncApRepPartEncoder implements Encoder, EncoderFactory
{
    /**
     * The application code constant for the {@link EncApRepPart} (27).
     */
    public static final int APPLICATION_CODE = 27;


    public Encoder getEncoder()
    {
        return new EncApRepPartEncoder();
    }


    public byte[] encode( Encodable apRepPart ) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ASN1OutputStream aos = new ASN1OutputStream( baos );

        DERSequence privPartSequence = encodeApRepPartSequence( ( EncApRepPart ) apRepPart );
        aos.writeObject( DERApplicationSpecific.valueOf( APPLICATION_CODE, privPartSequence ) );
        aos.close();

        return baos.toByteArray();
    }


    private DERSequence encodeApRepPartSequence( EncApRepPart message )
    {
        DERSequence sequence = new DERSequence();

        sequence.add( new DERTaggedObject( 0, KerberosTimeEncoder.encode( message.getClientTime() ) ) );
        sequence.add( new DERTaggedObject( 1, DERInteger.valueOf( message.getClientMicroSecond() ) ) );

        if ( message.getSubSessionKey() != null )
        {
            sequence.add( new DERTaggedObject( 2, EncryptionKeyEncoder.encodeSequence( message.getSubSessionKey() ) ) );
        }

        if ( message.getSequenceNumber() != null )
        {
            sequence.add( new DERTaggedObject( 3, DERInteger.valueOf( message.getSequenceNumber().intValue() ) ) );
        }

        return sequence;
    }
}
