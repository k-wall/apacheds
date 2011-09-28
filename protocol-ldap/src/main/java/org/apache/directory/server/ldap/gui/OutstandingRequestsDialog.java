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
package org.apache.directory.server.ldap.gui;


import java.awt.BorderLayout;
import java.net.InetSocketAddress;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JButton;

import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.LdapSession;
import org.apache.directory.shared.ldap.message.internal.InternalAbandonableRequest;

import javax.swing.JTextArea;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;


public class OutstandingRequestsDialog extends JDialog
{
    private static final long serialVersionUID = -3777123348215825711L;
    private static final InternalAbandonableRequest[] EMPTY_REQUEST_ARRAY = new InternalAbandonableRequest[0];
    private JPanel jContentPane;
    private JPanel jPanel;
    private JScrollPane jScrollPane;
    private JTable jTable;
    private JPanel jPanel1;
    private JButton jButton;

    final LdapSession session;
    final LdapServer ldapServer;

    private JPanel jPanel2;
    private JTextArea jTextArea;
    private JButton jButton1;
    private JButton jButton2;


    /**
     * This is the default constructor
     * @param owner the owning frame
     * @param session the MINA IoSession to get outstanding requests for
     * @param sessionRegistry the session registry
     */
    public OutstandingRequestsDialog( JFrame owner, LdapSession session, LdapServer ldapServer )
    {
        super( owner, true );
        this.session = session;
        this.ldapServer = ldapServer;

        StringBuffer buf = new StringBuffer();
        buf.append( "Outstanding Requests: " );
        buf.append( ( ( InetSocketAddress ) session.getIoSession().getRemoteAddress() ).getHostName() );
        buf.append( ":" );
        buf.append( ( ( InetSocketAddress ) session.getIoSession().getRemoteAddress() ).getPort() );
        setTitle( buf.toString() );
        initialize();
    }


    /**
     * This method initializes this
     */
    private void initialize()
    {
        this.setSize( 549, 341 );
        this.setContentPane( getJContentPane() );
    }


    /**
     * This method initializes jContentPane
     * 
     * @return javax.swing.JPanel
     */
    private JPanel getJContentPane()
    {
        if ( jContentPane == null )
        {
            jContentPane = new JPanel();
            jContentPane.setLayout( new BorderLayout() );
            jContentPane.add( getJPanel(), java.awt.BorderLayout.CENTER );
        }
        return jContentPane;
    }


    /**
     * This method initializes jPanel    
     *     
     * @return javax.swing.JPanel    
     */
    private JPanel getJPanel()
    {
        if ( jPanel == null )
        {
            jPanel = new JPanel();
            jPanel.setLayout( new BorderLayout() );
            jPanel.add( getJScrollPane(), java.awt.BorderLayout.CENTER );
            jPanel.add( getJPanel1(), java.awt.BorderLayout.SOUTH );
            jPanel.add( getJPanel2(), java.awt.BorderLayout.NORTH );
        }
        return jPanel;
    }


    /**
     * This method initializes jScrollPane    
     *     
     * @return javax.swing.JScrollPane    
     */
    private JScrollPane getJScrollPane()
    {
        if ( jScrollPane == null )
        {
            jScrollPane = new JScrollPane();
            jScrollPane.setViewportView( getJTable() );
        }
        return jScrollPane;
    }


    /**
     * This method initializes jTable    
     *     
     * @return javax.swing.JTable    
     */
    private JTable getJTable()
    {
        if ( jTable == null )
        {
            jTable = new JTable();
        }

        setRequestsModel();
        jTable.getSelectionModel().addListSelectionListener( new ListSelectionListener()
        {
            public void valueChanged( ListSelectionEvent e )
            {
                int row = jTable.getSelectedRow();
                if ( row > -1 )
                {
                    jButton2.setEnabled( true );
                    InternalAbandonableRequest req = ( ( OutstandingRequestsModel ) jTable.getModel() )
                        .getAbandonableRequest( row );
                    jTextArea.setText( req.toString() );
                    jTextArea.setEnabled( true );
                }
                else
                {
                    jButton2.setEnabled( false );
                    jTextArea.setText( "" );
                    jTextArea.setEnabled( false );
                }
            }
        } );
        return jTable;
    }


    private void setRequestsModel()
    {
        InternalAbandonableRequest[] requests;
        Map<Integer, InternalAbandonableRequest> reqsMap = session.getOutstandingRequests();
        
        if ( reqsMap != null )
        {
            requests = new InternalAbandonableRequest[reqsMap.size()];
            //noinspection unchecked
            requests = (org.apache.directory.shared.ldap.message.internal.InternalAbandonableRequest[] ) reqsMap.values().toArray( requests );
        }
        else
        {
            requests = EMPTY_REQUEST_ARRAY;
        }

        jTable.setModel( new OutstandingRequestsModel( requests ) );
    }


    /**
     * This method initializes jPanel1    
     *     
     * @return javax.swing.JPanel    
     */
    private JPanel getJPanel1()
    {
        if ( jPanel1 == null )
        {
            jPanel1 = new JPanel();
            jPanel1.add( getJButton(), null );
            jPanel1.add( getJButton1(), null );
        }
        return jPanel1;
    }


    /**
     * This method initializes jButton    
     *     
     * @return javax.swing.JButton    
     */
    private JButton getJButton()
    {
        if ( jButton == null )
        {
            jButton = new JButton();
            jButton.setText( "Done" );
            jButton.addActionListener( new java.awt.event.ActionListener()
            {
                public void actionPerformed( java.awt.event.ActionEvent e )
                {
                    OutstandingRequestsDialog.this.setVisible( false );
                    OutstandingRequestsDialog.this.dispose();
                }
            } );
        }
        return jButton;
    }


    /**
     * This method initializes jPanel2    
     *     
     * @return javax.swing.JPanel    
     */
    private JPanel getJPanel2()
    {
        if ( jPanel2 == null )
        {
            jPanel2 = new JPanel();
            jPanel2.setLayout( new BorderLayout() );
            jPanel2.setBorder( javax.swing.BorderFactory.createTitledBorder( null, "Request",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION, null, null ) );
            jPanel2.add( getJButton2(), java.awt.BorderLayout.WEST );
            jPanel2.add( getJTextArea(), java.awt.BorderLayout.CENTER );
        }
        return jPanel2;
    }


    /**
     * This method initializes jTextArea    
     *     
     * @return javax.swing.JTextArea    
     */
    private JTextArea getJTextArea()
    {
        if ( jTextArea == null )
        {
            jTextArea = new JTextArea();
        }

        jTextArea.setEnabled( false );
        jTextArea.setEditable( false );
        return jTextArea;
    }


    /**
     * This method initializes jButton1    
     *     
     * @return javax.swing.JButton    
     */
    private JButton getJButton1()
    {
        if ( jButton1 == null )
        {
            jButton1 = new JButton();
            jButton1.setText( "Refresh" );
            jButton1.addActionListener( new java.awt.event.ActionListener()
            {
                public void actionPerformed( java.awt.event.ActionEvent e )
                {
                    setRequestsModel();
                    jTextArea.setText( "" );
                    jTextArea.setEnabled( false );
                    jButton2.setEnabled( false );
                }
            } );
        }
        return jButton1;
    }


    /**
     * This method initializes jButton2    
     *     
     * @return javax.swing.JButton    
     */
    private JButton getJButton2()
    {
        if ( jButton2 == null )
        {
            jButton2 = new JButton();
            jButton2.setText( "Abandon" );
            jButton2.setEnabled( false );
            jButton2.addActionListener( new java.awt.event.ActionListener()
            {
                public void actionPerformed( java.awt.event.ActionEvent e )
                {
                    int row = jTable.getSelectedRow();
                    InternalAbandonableRequest req = ( ( OutstandingRequestsModel ) jTable.getModel() )
                        .getAbandonableRequest( row );
                    req.abandon();
                    session.abandonOutstandingRequest( req.getMessageId() );
                    setRequestsModel();
                }
            } );
        }
        return jButton2;
    }
} //  @jve:decl-index=0:visual-constraint="10,10"
