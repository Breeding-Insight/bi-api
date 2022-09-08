package org.breedinginsight.brapi.v2;

import io.micronaut.test.annotation.MockBean;
import lombok.SneakyThrows;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.daos.cache.ProgramCache;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProgramCacheUnitTest extends DatabaseTest {

    // TODO: Tests
    // POSTing skips refresh if one is already queued
    // POSTing refresh tracking separate for each program
    // Cache refresh failure invalidates cache

    Integer fetchCount = 0;
    Integer waitTime = 1000;
    Map<UUID, List<BrAPIGermplasm>> mockBrAPI = new HashMap<>();

    @MockBean(BrAPIGermplasmDAO.class)
    BrAPIGermplasmDAO germplasmDAO() { return mock(BrAPIGermplasmDAO.class);}
    @Inject
    BrAPIGermplasmDAO germplasmDAO;

    @AfterEach
    @SneakyThrows
    void setupNextTest() {
        // There is some thread sleeping used in this testing, wait for all processes to clean out between tests
        Thread.sleep(5000);
        fetchCount = 0;
        mockBrAPI = new HashMap<>();
    }

    @SneakyThrows
    public Map<String, BrAPIGermplasm> mockFetch(UUID programId, Integer sleepTime) {
        fetchCount += 1;
        System.out.println("Starting sleep at " + LocalDateTime.now());
        Thread.sleep(sleepTime);
        System.out.println("Woken up at " + LocalDateTime.now());
        return mockBrAPI.containsKey(programId) ? new HashMap<>(mockBrAPI.get(programId).stream().collect(Collectors.toMap(germplasm -> UUID.randomUUID().toString(), germplasm -> germplasm))) : new HashMap<>();
    }

    @SneakyThrows
    public List<BrAPIGermplasm> mockPost(UUID programId, List<BrAPIGermplasm> germplasm) {
        if (!mockBrAPI.containsKey(programId)) {
            mockBrAPI.put(programId, germplasm);
        } else {
            List<BrAPIGermplasm> allGermplasm = mockBrAPI.get(programId);
            allGermplasm.addAll(germplasm);
        }
        return germplasm;
    }

    @Test
    @SneakyThrows
    public void populatedRefreshQueueSkipsRefresh() throws Exception {
        // Make a lot of post calls and just how many times the fetch method is called
        ProgramCache<BrAPIGermplasm> cache = new ProgramCache<>(DatabaseTest.getRedisConnection(), (UUID id) -> mockFetch(id, waitTime), BrAPIGermplasm.class);
        UUID programId = UUID.randomUUID();
        int numPost = 10;
        int currPost = 0;
        while (currPost < numPost) {
            List<BrAPIGermplasm> newList = new ArrayList<>();
            newList.add(new BrAPIGermplasm().germplasmDbId(UUID.randomUUID().toString()));
            cache.post(programId, () -> mockPost(programId, new ArrayList<>(newList)));
            currPost += 1;
        }
        assertTrue(fetchCount < numPost, "A fetch call was made for every post. It shouldn't.");
        assertEquals(1, mockBrAPI.size(), "More than one program existed in mocked brapi db.");
        assertEquals(numPost, mockBrAPI.get(programId).size(), "Wrong number of germplasm in db");
        while(cache.isRefreshing(programId)) {
            Thread.sleep(waitTime);
        }
        Map<String, BrAPIGermplasm> cachedGermplasm = cache.get(programId);
        assertEquals(numPost, cachedGermplasm.size(), "Wrong number of germplasm in cache");
    }

    @Test
    @SneakyThrows
    public void programRefreshesSeparated() throws Exception {
        // Make a lot of post calls on different programs to check that they don't wait for each other
        ProgramCache<BrAPIGermplasm> cache = new ProgramCache<>(DatabaseTest.getRedisConnection(), (UUID id) -> mockFetch(id, waitTime), BrAPIGermplasm.class);
        int numPost = 10;
        int currPost = 0;
        while (currPost < numPost) {
            UUID id = UUID.randomUUID();
            List<BrAPIGermplasm> newList = new ArrayList<>();
            newList.add(new BrAPIGermplasm().germplasmDbId(UUID.randomUUID().toString()));
            cache.post(id, () -> mockPost(id, new ArrayList<>(newList)));
            // This doesn't have to do with our code, our mock function is just tripping over itself trying to update the number of fetches
            Thread.sleep(waitTime/5);
            currPost += 1;
        }
        assertEquals(numPost, fetchCount, "A fetch call should have been made for every post");
        assertEquals(numPost, mockBrAPI.size(), "Less programs existed than existed in mock brapi db.");
        for (UUID key: mockBrAPI.keySet()) {
            assertEquals(1, mockBrAPI.get(key).size(), "Wrong number of germplasm in db");
            assertEquals(1, cache.get(key).size(), "Wrong number of germplasm in cache");
        }
    }

    @Test
    @SneakyThrows
    public void onlyOneRefreshIsQueued() throws ApiException {
        // Test that the get method waits for an ongoing refresh to finish when there isn't any day
        UUID programId = UUID.randomUUID();
        List<BrAPIGermplasm> newList = new ArrayList<>();
        newList.add(new BrAPIGermplasm().germplasmDbId(UUID.randomUUID().toString()));
        mockBrAPI.put(programId, new ArrayList<>(newList));

        ProgramCache<BrAPIGermplasm> cache = new ProgramCache<>(DatabaseTest.getRedisConnection(), (UUID id) -> mockFetch(id, waitTime*3), BrAPIGermplasm.class);
        cache.populate(programId);
        cache.get(programId);
        cache.get(programId);
        // Our fetch method should have only been called once for the initial loading
        assertEquals(2, fetchCount, "Fetch method was called on every get");
    }

    @Test
    @SneakyThrows
    public void getMethodDoesNotWaitForRefresh() throws Exception {
        // Test that the get method does not wait for a refresh when there is data present
        UUID programId = UUID.randomUUID();
        List<BrAPIGermplasm> newList = new ArrayList<>();
        newList.add(new BrAPIGermplasm().germplasmDbId(UUID.randomUUID().toString()));
        mockBrAPI.put(programId, new ArrayList<>(newList));
        ProgramCache<BrAPIGermplasm> cache = new ProgramCache<>(DatabaseTest.getRedisConnection(), (UUID id) -> mockFetch(id, waitTime), BrAPIGermplasm.class);
        cache.populate(programId);
        Callable<List<BrAPIGermplasm>> postFunction = () -> mockPost(programId, new ArrayList<>(newList));

        // Get waits for initial fetch
        Map<String, BrAPIGermplasm> cachedGermplasm = cache.get(programId);
        assertEquals(1, cachedGermplasm.size(), "Initial germplasm not as expected");

        // Now post another object and call get immediately to see that it returns the old data
        cache = new ProgramCache<>(DatabaseTest.getRedisConnection(), (UUID id) -> mockFetch(id, waitTime*4), BrAPIGermplasm.class);
        cache.post(programId, postFunction);
        System.out.println("calling get at: "+ LocalDateTime.now());
        cachedGermplasm = cache.get(programId);
        assertEquals(1, cachedGermplasm.size(), "Get method seemed to have waited for refresh method");

        // Now wait for the fetch after the post to finish
        Thread.sleep(waitTime*5);
        cachedGermplasm = cache.get(programId);
        assertEquals(2, cachedGermplasm.size(), "Get method did not get updated germplasm");
    }

    @Test
    @SneakyThrows
    @Order(1)
    public void refreshErrorInvalidatesCache() throws Exception {
        // Tests that data is invalidated when a refresh method fails
        // Tests that data is no longer retrievable when invalidated and needs to be refreshed

        // Set starter data
        UUID programId = UUID.randomUUID();
        List<BrAPIGermplasm> newList = new ArrayList<>();
        newList.add(new BrAPIGermplasm().germplasmDbId(UUID.randomUUID().toString()));
        mockBrAPI.put(programId, new ArrayList<>(newList));

        // Start cache
        ProgramCache<BrAPIGermplasm> cache = new ProgramCache<>(DatabaseTest.getRedisConnection(), (UUID id) -> mockFetch(programId, waitTime), BrAPIGermplasm.class);
        cache.populate(programId);

        //wait for the initial fetch to complete
        Thread.sleep(waitTime*2);

        Map<String, BrAPIGermplasm> cachedGermplasm = cache.get(programId);
        assertEquals(1, cachedGermplasm.size(), "Initial germplasm not as expected");

        // Change our fetch method to throw an error now
        cache = new ProgramCache<>(DatabaseTest.getRedisConnection(), (UUID id) -> {throw new ApiException("UhOh");}, BrAPIGermplasm.class);
        cache.post(programId, () -> mockPost(programId, new ArrayList<>(newList)));
        // Give it a second so we can wait for the cache to be invalidated and refresh to finish
        Thread.sleep(waitTime*2);

        // Check that the fetch function needs to be called again since the cache was invalidated
        cache = new ProgramCache<>(DatabaseTest.getRedisConnection(), (UUID id) -> mockFetch(programId, waitTime), BrAPIGermplasm.class);
        assertEquals(1, fetchCount);
        cachedGermplasm = cache.get(programId);
        Thread.sleep(waitTime*2);
        assertEquals(2, fetchCount);
        assertEquals(2, cachedGermplasm.size(), "Newly retrieved germplasm not as expected");
    }
}
