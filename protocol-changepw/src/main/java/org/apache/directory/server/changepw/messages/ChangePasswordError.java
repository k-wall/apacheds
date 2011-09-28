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
package org.apache.directory.server.changepw.messages;


import org.apache.directory.server.kerberos.shared.messages.ErrorMessage;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 642496 $, $Date: 2008-03-29 04:09:22 +0100 (Sam, 29 mar 2008) $
 */
public class ChangePasswordError extends AbstractPasswordMessage
{
    private ErrorMessage errorMessage;


    /**
     * Creates a new instance of ChangePasswordError.
     *
     * @param versionNumber
     * @param errorMessage
     */
    public ChangePasswordError( short versionNumber, ErrorMessage errorMessage )
    {
        super( versionNumber );

        this.errorMessage = errorMessage;
    }


    /**
     * Returns the {@link ErrorMessage}.
     *
     * @return The {@link ErrorMessage}.
     */
    public ErrorMessage getErrorMessage()
    {
        return errorMessage;
    }
}
