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
package org.apache.directory.server.kerberos.shared.messages.value;


import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.directory.server.kerberos.shared.crypto.checksum.ChecksumType;
import org.junit.Test;
import static org.junit.Assert.assertTrue;


/**
 * Test the Checksum encoding and decoding
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 542147 $, $Date: 2007-05-28 10:14:21 +0200 (Mon, 28 May 2007) $
 */
public class ChecksumTest
{
    @Test
    public void testEncodingChecksum() throws Exception
    {
        Checksum chk = new Checksum( ChecksumType.CRC32, new byte[]
            { 0x01, 0x02, 0x03 } );

        ByteBuffer encoded = ByteBuffer.allocate( chk.computeLength() );

        chk.encode( encoded );

        byte[] expectedResult = new byte[]
            { 
            0x30, 0x0c, 
              (byte)0xA0, 0x03, 
                0x02, 0x01, 0x01, 
              (byte)0xA1, 0x05, 
                0x04, 0x03, 
                  0x01, 0x02, 0x03 
            };

        assertTrue( Arrays.equals( expectedResult, encoded.array() ) );
    }


    @Test
    public void testEncodingNullChecksum() throws Exception
    {
        Checksum chk = new Checksum( ChecksumType.CRC32, null );

        ByteBuffer encoded = ByteBuffer.allocate( chk.computeLength() );

        chk.encode( encoded );

        byte[] expectedResult = new byte[]
            { 
                0x30, 0x09, 
                  ( byte ) 0xA0, 
                    0x03, 0x02, 0x01, 0x01, 
                  ( byte ) 0xA1, 0x02, 
                    0x04, 0x00 
            };

        assertTrue( Arrays.equals( expectedResult, encoded.array() ) );
    }


    /**
     * Tests that two Checksums are equal if both their type and value are equal.
     */
    @Test
    public void testEquality()
    {
        byte[] checksumValue =
            { ( byte ) 0x30, ( byte ) 0x1A, ( byte ) 0xA0, ( byte ) 0x11, ( byte ) 0x18, ( byte ) 0x0F, ( byte ) 0x32,
                ( byte ) 0x30 };

        Checksum expected = new Checksum( ChecksumType.RSA_MD5, checksumValue );
        Checksum provided = new Checksum( ChecksumType.RSA_MD5, checksumValue );

        assertTrue( "Checksum equality", expected.equals( provided ) );
    }
}
