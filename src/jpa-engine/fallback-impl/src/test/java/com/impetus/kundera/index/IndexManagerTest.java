package com.impetus.kundera.index;

import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.impetus.kundera.client.Client;
import com.impetus.kundera.client.CoreTestClient;
import com.impetus.kundera.metadata.KunderaMetadataManager;
import com.impetus.kundera.metadata.entities.EmbeddableEntity;
import com.impetus.kundera.metadata.entities.EmbeddableEntityTwo;
import com.impetus.kundera.metadata.entities.SingularEntityEmbeddable;
import com.impetus.kundera.metadata.model.Employe;
import com.impetus.kundera.metadata.model.EntityMetadata;
import com.impetus.kundera.metadata.model.PropertyIndex;
import com.impetus.kundera.persistence.EntityManagerFactoryImpl;
import com.impetus.kundera.query.Person;
import com.impetus.kundera.query.Person.Day;
import com.impetus.kundera.utils.LuceneCleanupUtilities;

public class IndexManagerTest
{

    private static final String LUCENE_DIR_PATH = "./lucene";
    
    private EntityManagerFactory emf;
    private EntityManager em;

    @Before
    public void setup()
    {
        emf = Persistence.createEntityManagerFactory("patest");
        em = emf.createEntityManager();
    }

    @Test
    public void testCRUD()
    {
        LuceneIndexer indexer = LuceneIndexer.getInstance(LUCENE_DIR_PATH);
        IndexManager ixManager = new IndexManager(indexer, ((EntityManagerFactoryImpl)emf).getKunderaMetadataInstance());

        EntityMetadata metadata = KunderaMetadataManager.getEntityMetadata(((EntityManagerFactoryImpl)emf).getKunderaMetadataInstance(), Person.class);
        Person p = new Person();
        p.setAge(32);
        p.setDay(Day.TUESDAY);
        p.setPersonId("p1");

        Assert.assertNotNull(ixManager.getIndexer());
        Assert.assertEquals(indexer, ixManager.getIndexer());
        
        ixManager.write(metadata, p);

        String luceneQuery = "+Person.AGE:32 AND +entity.class:com.impetus.kundera.query.Person";

        try
        {
            Map<String, Object> results = ixManager.search(metadata.getEntityClazz(), luceneQuery, 0, 10, false);
            Assert.assertTrue(!results.isEmpty());
        }
        catch (LuceneIndexingException liex)
        {
            Assert.fail();
        }

        p.setAge(35);
        
        ixManager.update(metadata, p, null, Person.class);
        
        luceneQuery = "+Person.AGE:35 AND +entity.class:com.impetus.kundera.query.Person";
        
        try
        {
            Map<String, Object> results = ixManager.search(metadata.getEntityClazz(), luceneQuery,1);
            Assert.assertTrue(!results.isEmpty());
        }
        catch (LuceneIndexingException liex)
        {
            Assert.fail();
        }
        
        // Remove indexes.
        ixManager.remove(metadata, p, "p1");
        
        luceneQuery = "+Person.AGE:32 AND +entity.class:com.impetus.kundera.query.Person";
        
        try
        {
            Map<String, Object> results = ixManager.search(metadata.getEntityClazz(), luceneQuery, 0, 10, false);
            Assert.assertTrue(results.isEmpty());
        }
        catch (LuceneIndexingException liex)
        {
            Assert.fail();
        }
    }


    @Test
    public void testEmbeddable()
    {
        LuceneIndexer indexer = LuceneIndexer.getInstance(LUCENE_DIR_PATH);
        IndexManager ixManager = new IndexManager(indexer, ((EntityManagerFactoryImpl)emf).getKunderaMetadataInstance());

        EntityMetadata metadata = KunderaMetadataManager.getEntityMetadata(((EntityManagerFactoryImpl)emf).getKunderaMetadataInstance(), SingularEntityEmbeddable.class);
        SingularEntityEmbeddable entity = new SingularEntityEmbeddable();
        entity.setKey(1);
        entity.setName("entity");
        entity.setField("name");
        
        EmbeddableEntity embed1 = new EmbeddableEntity();
        embed1.setField("embeddedField1");
        
        EmbeddableEntityTwo embed2 = new EmbeddableEntityTwo();
        embed2.setField(1f);
        embed2.setName("name");

        entity.setEmbeddableEntity(embed1);
        entity.setEmbeddableEntityTwo(embed2);
        
        em.persist(entity);
        
        String luceneQuery = "+entity.class:com.impetus.kundera.metadata.entities.SingularEntityEmbeddable AND +SingularEntityEmbeddable.embeddedField:embeddedField1";
        
        Map<String, Object> results = ixManager.search(metadata.getEntityClazz(), luceneQuery, 0, 10, false);
        
        Assert.assertFalse(results.isEmpty());
        
    }
    
    @Test
    public void testEmbeddableCustomIndexer()
    {
        try
        {
            CoreTestIndexer indexer = new CoreTestIndexer();
            IndexManager ixManager = new IndexManager(indexer, ((EntityManagerFactoryImpl)emf).getKunderaMetadataInstance());
            
            Map<String, Client> clients = (Map<String, Client>) em.getDelegate();
            CoreTestClient client = (CoreTestClient)clients.get("patest");
            client.setIndexManager(ixManager);

            EntityMetadata metadata = KunderaMetadataManager.getEntityMetadata(((EntityManagerFactoryImpl)emf).getKunderaMetadataInstance(), SingularEntityEmbeddable.class);
            SingularEntityEmbeddable entity = new SingularEntityEmbeddable();
            entity.setKey(1);
            entity.setName("entity");
            entity.setField("name");
            
            EmbeddableEntity embed1 = new EmbeddableEntity();
            embed1.setField("embeddedField1");
            
            EmbeddableEntityTwo embed2 = new EmbeddableEntityTwo();
            embed2.setField(1f);
            embed2.setName("name");

            entity.setEmbeddableEntity(embed1);
            entity.setEmbeddableEntityTwo(embed2);
            
            em.persist(entity);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
        }       
    }
    
    @Test 
    public void testPropertyIndex()
    {
        try
        {
            PropertyIndex pi = new PropertyIndex(Employe.class.getDeclaredField("empName"), "EMP_NAME", "ASC");
            pi.setBoost(1.2f);
            
            Assert.assertEquals(1.2f, pi.getBoost());
            Assert.assertEquals("ASC", pi.getIndexType());            
        }
        catch (SecurityException e)
        {
            
            Assert.fail(e.getMessage());
        }
        catch (NoSuchFieldException e)
        {
            Assert.fail(e.getMessage());
        }
    }
    
    @After
    public void tearDown()
    {
        LuceneCleanupUtilities.cleanDir(LUCENE_DIR_PATH);
    }

}
