package org.apache.directory.server.dns.store.jndi;

import java.util.Set;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.dns.DnsException;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.RecordStore;

@Deprecated
public class JndiRecordStoreImpl implements RecordStore {

  public JndiRecordStoreImpl(String a, String b, DirectoryService ds) {
    throw new IllegalStateException("Not supported!");
  }
  
  public Set<ResourceRecord> getRecords(QuestionRecord question) throws DnsException {
    throw new UnsupportedOperationException();
  }
}
