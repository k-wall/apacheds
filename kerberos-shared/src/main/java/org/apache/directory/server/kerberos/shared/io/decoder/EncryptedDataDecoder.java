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

import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptedData;
import org.apache.directory.shared.asn1.der.ASN1InputStream;
import org.apache.directory.shared.asn1.der.DEREncodable;
import org.apache.directory.shared.asn1.der.DERInteger;
import org.apache.directory.shared.asn1.der.DEROctetString;
import org.apache.directory.shared.asn1.der.DERSequence;
import org.apache.directory.shared.asn1.der.DERTaggedObject;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 587682 $, $Date: 2007-10-24 00:47:43 +0200 (Mer, 24 oct 2007) $
 */
public class EncryptedDataDecoder
{
    /**
     * Decodes a byte array into an {@link EncryptedData}.
     *
     * @param encodedEncryptedData
     * @return The {@link EncryptedData}.
     * @throws IOException
     */
    public static EncryptedData decode( byte[] encodedEncryptedData ) throws IOException
    {
        ASN1InputStream ais = new ASN1InputStream( encodedEncryptedData );

        DERSequence sequence = ( DERSequence ) ais.readObject();

        return decode( sequence );
    }


    /**
     * Decodes a {@link DERSequence} into an {@link EncryptedData}.
     * 
     * EncryptedData ::=   SEQUENCE {
     *             etype[0]     INTEGER, -- EncryptionEngine
     *             kvno[1]      INTEGER OPTIONAL,
     *             cipher[2]    OCTET STRING -- ciphertext
     * }
     * 
     * @param sequence 
     * @return The {@link EncryptedData}.
     */
    public static EncryptedData decode( DERSequence sequence )
    {
        EncryptedData encryptedData = new EncryptedData();

        for ( Enumeration<DEREncodable> e = sequence.getObjects(); e.hasMoreElements(); )
        {
            DERTaggedObject object = ( DERTaggedObject ) e.nextElement();
            int tag = object.getTagNo();
            DEREncodable derObject = object.getObject();

            switch ( tag )
            {
                case 0:
                    DERInteger etype = ( DERInteger ) derObject;
                    encryptedData.setEType( EncryptionType.getTypeByOrdinal( etype.intValue() ) );
                    break;
                    
                case 1:
                    DERInteger kvno = ( DERInteger ) derObject;
                    encryptedData.setKvno( kvno.intValue() );
                    break;
                    
                case 2:
                    DEROctetString cipher = ( DEROctetString ) derObject;
                    encryptedData.setCipher( cipher.getOctets() );
                    break;
            }
        }

        return encryptedData;
    }
}
