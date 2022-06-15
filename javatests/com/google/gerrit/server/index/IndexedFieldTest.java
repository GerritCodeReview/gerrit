// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.index;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.IndexedField.SearchSpec;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.StoredValue;
import com.google.gerrit.index.testing.FakeStoredValue;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Tests for {@link com.google.gerrit.index.IndexedField} */
@RunWith(Theories.class)
public class IndexedFieldTest {

  /** Test input object for {@link IndexedField} */
  static class TestIndexedData {

    private Object testField;

    public Object getTestField() {
      return testField;
    }

    public void setTestField(Object testField) {
      this.testField = testField;
    }
  }

  static IndexedField<TestIndexedData, Integer> INTEGER_FIELD =
      IndexedField.<TestIndexedData>integerBuilder("TestField")
          .build(
              testData -> (Integer) testData.getTestField(),
              (testIndexedData, value) -> testIndexedData.setTestField(value));

  static SearchSpec INTEGER_FIELD_SPEC = INTEGER_FIELD.integer("test");

  static IndexedField<TestIndexedData, Iterable<String>> ITERABLE_STRING_FIELD =
      IndexedField.<TestIndexedData>iterableStringBuilder("TestField")
          .build(
              testData -> (Iterable<String>) testData.getTestField(),
              (testIndexedData, value) -> testIndexedData.setTestField(value));

  static SearchSpec ITERABLE_STRING_FIELD_SPEC = ITERABLE_STRING_FIELD.fullText("test");

  static IndexedField<TestIndexedData, Iterable<byte[]>> ITERABLE_STORED_BYTE_FIELD =
      IndexedField.<TestIndexedData>iterableByteArrayBuilder("TestField")
          .stored()
          .build(
              testData -> (Iterable<byte[]>) testData.getTestField(),
              (testIndexedData, value) -> testIndexedData.setTestField(value));

  static SearchSpec ITERABLE_STORED_BYTE_SPEC = ITERABLE_STORED_BYTE_FIELD.storedOnly("test");

  static IndexedField<TestIndexedData, byte[]> STORED_BYTE_FIELD =
      IndexedField.<TestIndexedData>byteArrayBuilder("TestField")
          .stored()
          .build(
              testData -> (byte[]) testData.getTestField(),
              (testIndexedData, value) -> testIndexedData.setTestField(value));

  static SearchSpec STORED_BYTE_SPEC = STORED_BYTE_FIELD.storedOnly("test");

  @DataPoints("nonProtoTypes")
  public static final ImmutableList<Entry<IndexedField.SearchSpec, Serializable>>
      fieldToStoredValue =
          ImmutableMap.of(
                  INTEGER_FIELD_SPEC,
                  123456,
                  ITERABLE_STRING_FIELD_SPEC,
                  ImmutableList.of("123456"),
                  ITERABLE_STORED_BYTE_SPEC,
                  ImmutableList.of("123456".getBytes(StandardCharsets.UTF_8)),
                  STORED_BYTE_SPEC,
                  "123456".getBytes(StandardCharsets.UTF_8))
              .entrySet()
              .asList();

  @Theory
  public void testSetIfPossible(
      @FromDataPoints("nonProtoTypes") Entry<SearchSpec, Object> fieldToStoredValue) {
    Object docValue = fieldToStoredValue.getValue();
    SchemaField searchSpec = fieldToStoredValue.getKey();
    StoredValue storedValue = new FakeStoredValue(fieldToStoredValue.getValue());
    TestIndexedData testIndexedData = new TestIndexedData();
    searchSpec.setIfPossible(testIndexedData, storedValue);
    assertThat(testIndexedData.getTestField()).isEqualTo(docValue);
  }
}
