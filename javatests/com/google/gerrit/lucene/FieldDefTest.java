package com.google.gerrit.lucene;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.query.change.ChangeData;
import org.apache.lucene.index.IndexableField;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.google.gerrit.server.index.change.ChangeField.ADDED;

public class FieldDefTest {

  @Test
  public void nullPointerExceptionWhenEmptyIndexableFieldsList() {
    Project.NameKey project = Project.nameKey("project");
    ChangeData cd = ChangeData.createForTest(project, Change.id(1), 1, ObjectId.zeroId());
    List <IndexableField> indexableFields = Collections.emptyList();

    ADDED.setIfPossible(cd, new LuceneStoredValue(indexableFields));
  }
}
