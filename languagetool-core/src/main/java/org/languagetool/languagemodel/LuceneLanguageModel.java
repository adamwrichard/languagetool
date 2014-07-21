/* LanguageTool, a natural language style checker 
 * Copyright (C) 2014 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.languagemodel;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Information about ngram occurrences, taken from a Lucene index.
 * @since 2.7
 */
public class LuceneLanguageModel implements LanguageModel {

  private final List<File> indexes = new ArrayList<>();
  private final Map<Integer,LuceneSearcher> luceneSearcherMap = new HashMap<>();
  
  public LuceneLanguageModel(File topIndexDir) throws IOException {
    addIndex(topIndexDir, 2);
    addIndex(topIndexDir, 3);
    if (luceneSearcherMap.size() == 0) {
      throw new RuntimeException("No directories '2grams' and/or '3grams' found in " + topIndexDir);
    }
  }

  private void addIndex(File topIndexDir, int ngramSize) throws IOException {
    File indexDir = new File(topIndexDir, ngramSize + "grams");
    if (indexDir.exists() && indexDir.isDirectory()) {
      luceneSearcherMap.put(ngramSize, new LuceneSearcher(indexDir));
      indexes.add(indexDir);
    }
  }

  @Override
  public long getCount(String token1, String token2) {
    Term term = new Term("ngram", token1 + " " + token2);
    LuceneSearcher luceneSearcher = getLuceneSearcher(2);
    return getCount(term, luceneSearcher);
  }

  @Override
  public long getCount(String token1, String token2, String token3) {
    Term term = new Term("ngram", token1 + " " + token2 + " " + token3);
    LuceneSearcher luceneSearcher = getLuceneSearcher(3);
    return getCount(term, luceneSearcher);
  }

  private LuceneSearcher getLuceneSearcher(int ngramSize) {
    LuceneSearcher luceneSearcher = luceneSearcherMap.get(ngramSize);
    if (luceneSearcher == null) {
      throw new RuntimeException("No " + ngramSize + "grams index found, use the appropriate constructor to specify one");
    }
    return luceneSearcher;
  }

  private long getCount(Term term, LuceneSearcher luceneSearcher) {
    try {
      TopDocs docs = luceneSearcher.searcher.search(new TermQuery(term), 1);
      if (docs.totalHits > 0) {
        int docId = docs.scoreDocs[0].doc;
        return Long.parseLong(luceneSearcher.reader.document(docId).get("count"));
      } else {
        return 0;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    for (LuceneSearcher searcher : luceneSearcherMap.values()) {
      try {
        searcher.reader.close();
        searcher.directory.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public String toString() {
    return indexes.toString();
  }

  private class LuceneSearcher {
    final FSDirectory directory;
    final IndexReader reader;
    final IndexSearcher searcher;
    private LuceneSearcher(File indexDir) throws IOException {
      this.directory = FSDirectory.open(indexDir);
      this.reader = DirectoryReader.open(directory);
      this.searcher = new IndexSearcher(reader);
    }
  }
}