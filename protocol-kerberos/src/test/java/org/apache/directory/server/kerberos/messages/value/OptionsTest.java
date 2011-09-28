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
package org.apache.directory.server.kerberos.messages.value;


import java.util.Arrays;

import org.apache.directory.server.kerberos.shared.messages.value.KdcOptions;
import org.apache.directory.server.kerberos.shared.messages.value.flags.TicketFlag;
import org.apache.directory.server.kerberos.shared.messages.value.flags.TicketFlags;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 896599 $, $Date: 2010-01-06 19:26:43 +0100 (Mer, 06 jan 2010) $
 */
public class OptionsTest
{
    private byte[] fpriOptions =
        { ( byte ) 0x50, ( byte ) 0x00, ( byte ) 0x00, ( byte ) 0x10 };


    /**
     * Tests converting the ticket flags to a descriptive String.
     */
    @Test
    public void testToString()
    {
        TicketFlags flags = new TicketFlags();
        flags.setFlag( TicketFlag.FORWARDABLE );
        flags.setFlag( TicketFlag.PROXIABLE );
        flags.setFlag( TicketFlag.RENEWABLE );
        flags.setFlag( TicketFlag.INITIAL );
        assertEquals( flags.toString(), "FORWARDABLE(1) PROXIABLE(3) RENEWABLE(8) INITIAL(9)" );
    }


    /**
     * Tests that setting flags is idempotent.
     */
    @Test
    public void testDuplicateSetting()
    {
        TicketFlags flags = new TicketFlags();
        flags.setFlag( TicketFlag.MAY_POSTDATE );
        flags.setFlag( TicketFlag.FORWARDABLE );
        flags.setFlag( TicketFlag.PROXIABLE );
        flags.setFlag( TicketFlag.MAY_POSTDATE );
        flags.setFlag( TicketFlag.RENEWABLE );
        assertEquals( flags.toString(), "FORWARDABLE(1) PROXIABLE(3) MAY_POSTDATE(5) RENEWABLE(8)" );
    }


    /**
     * Tests the basic construction of the {@link KdcOptions}.
     */
    @Test
    public void testConstruction()
    {
        KdcOptions options = new KdcOptions( fpriOptions );
        assertTrue( Arrays.equals( options.getBytes(), fpriOptions ) );
    }
}
