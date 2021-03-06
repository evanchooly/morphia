package org.mongodb.morphia.issue502;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mongodb.morphia.TestBase;
import org.mongodb.morphia.annotations.Id;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Can't inherit HashSet : generic is lost...
 */
public class CollectionInheritanceTest extends TestBase {

    private static Book newBook() {
        final Book book = new Book();
        book.authors.add(new Author("Hergé"));
        book.authors.add(new Author("George R. R. Martin"));
        return book;
    }

    /**
     * Issue's details...
     */
    @Test
    public void testMappingBook() {
        // Mapping...
        getMapper().map(Book.class /* , Authors.class, Author.class */);

        // Test mapping : author objects must be converted into Document (but wasn't)
        final Document dbBook = getMapper().toDocument(newBook());
        final Object firstBook = ((List<?>) dbBook.get("authors")).iterator().next();
        assertTrue("Author wasn't converted : expected instanceof <Document>, but was <" + firstBook.getClass() + ">",
                   firstBook instanceof Document);

    }

    /**
     * Real test
     */
    @Test
    public void testSavingBook() {
        // Test saving
        getDatastore().save(newBook());

        assertEquals(1, getDatastore().getCollection(Book.class).count());
    }

    private static class Author {
        private String name;

        private Author() {
        }

        Author(final String name) {
            this.name = name;
        }

    }

    private static class Authors extends HashSet<Author> {
    }

    private static class Book {
        @Id
        private ObjectId id;

        private Authors authors = new Authors();
    }
}
