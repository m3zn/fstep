package com.cgi.eoss.fstep.catalogue.resto;

import com.cgi.eoss.fstep.catalogue.CatalogueException;
import com.cgi.eoss.fstep.catalogue.IngestionException;
import com.cgi.eoss.fstep.catalogue.util.GeoUtil;
import com.cgi.eoss.fstep.model.FstepFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import lombok.extern.log4j.Log4j2;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.geojson.Feature;
import org.geojson.GeoJsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * <p>Implementation of RestoService using Resto's HTTP REST-style API.</p>
 */
@Component
@Log4j2
public class RestoServiceImpl implements RestoService {

    private final OkHttpClient client;

    private final ObjectMapper jsonMapper;

    private final String restoBaseUrl;

    @Value("${fstep.catalogue.resto.enabled:true}")
    private boolean restoEnabled;

    @Value("${fstep.catalogue.resto.collection.externalProducts:fstepInputs}")
    private String externalProductCollection;

    @Value("${fstep.catalogue.resto.collection.externalProductsModel:RestoModel_Fstep_Input}")
    private String externalProductModel;

    @Value("${fstep.catalogue.resto.collection.refData:fstepRefData}")
    private String refDataCollection;

    @Value("${fstep.catalogue.resto.collection.refDataModel:RestoModel_Fstep_Reference}")
    private String refDataModel;

    @Value("${fstep.catalogue.resto.collection.outputProducts:fstepOutputs}")
    private String outputProductCollection;

    @Value("${fstep.catalogue.resto.collection.outputProductsModel:RestoModel_Fstep_Output}")
    private String outputProductModel;

    @Autowired
    public RestoServiceImpl(@Value("${fstep.catalogue.resto.url:http://fstep-resto/resto/}") String restoBaseUrl,
                            @Value("${fstep.catalogue.resto.username:fstepresto}") String username,
                            @Value("${fstep.catalogue.resto.password:fsteprestopass}") String password) {
        this.restoBaseUrl = restoBaseUrl;
        this.client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Request authenticatedRequest = request.newBuilder()
                                .header("Authorization", Credentials.basic(username, password))
                                .build();
                        return chain.proceed(authenticatedRequest);
                    }
                })
                .addInterceptor(new HttpLoggingInterceptor(LOG::trace).setLevel(HttpLoggingInterceptor.Level.BODY))
                .build();
        jsonMapper = new ObjectMapper();
    }

    @Override
    public UUID ingestReferenceData(GeoJsonObject object) {
        return ingest(refDataCollection, object);
    }

    @Override
    public UUID ingestOutputProduct(GeoJsonObject object) {
        return ingest(outputProductCollection, object);
    }

    @Override
    public UUID ingestExternalProduct(String collection, GeoJsonObject object) {
        return ingest(collection, object);
    }

    @Override
    public void deleteReferenceData(UUID restoId) {
        delete(refDataCollection, restoId);
    }

    @Override
    public GeoJsonObject getGeoJson(FstepFile fstepFile) {
        HttpUrl url = HttpUrl.parse(restoBaseUrl).newBuilder()
                .addPathSegment("api")
                .addPathSegment("collections")
                .addPathSegment("search.json")
                .addQueryParameter("identifier", fstepFile.getRestoId().toString())
                .build();

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {
            Configuration configuration = Configuration.defaultConfiguration().mappingProvider(new JacksonMappingProvider());
            return JsonPath.using(configuration).parse(response.body().string()).read("$.features[0]", Feature.class);
        } catch (Exception e) {
            LOG.error("Failed to query Resto for GeoJson for identifier {}", fstepFile.getRestoId(), e);
            throw new RestoException(e);
        }
    }

    @Override
    public GeoJsonObject getGeoJsonSafe(FstepFile fstepFile) {
        try {
            return getGeoJson(fstepFile);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getReferenceDataCollection() {
        return refDataCollection;
    }

    @Override
    public String getOutputProductsCollection() {
        return outputProductCollection;
    }

    private UUID ingest(String collection, GeoJsonObject object) {
        if (!restoEnabled) {
            UUID dummy = UUID.randomUUID();
            LOG.warn("Resto is disabled; 'ingested' dummy feature: {}", dummy);
            return dummy;
        }

        HttpUrl url = HttpUrl.parse(restoBaseUrl).newBuilder().addPathSegment("collections").addPathSegment(collection).build();
        LOG.debug("Creating new Resto catalogue entry in collection: {}", collection);

        try {
            ensureCollectionExists(collection);
        } catch (Exception e) {
            throw new IngestionException("Failed to get/create Resto collection", e);
        }

        String geojson = GeoUtil.geojsonToString(object);
        LOG.debug("Ingesting GeoJSON to {}: {}", geojson);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse(APPLICATION_JSON_VALUE), geojson))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String uuid = JsonPath.read(response.body().string(), "$.featureIdentifier");
                LOG.info("Created new Resto object with ID: {}", uuid);
                return UUID.fromString(uuid);
            } else {
                LOG.error("Failed to ingest Resto object to collection '{}': {} {}: {}", collection, response.code(), response.message(), response.body());
                throw new IngestionException("Failed to ingest Resto object");
            }
        } catch (Exception e) {
            throw new IngestionException(e);
        }
    }

    private void delete(String collection, UUID uuid) {
        if (!restoEnabled) {
            LOG.warn("Resto is disabled; no deletion occurring for {}", uuid);
            return;
        }

        HttpUrl url = HttpUrl.parse(restoBaseUrl).newBuilder().addPathSegment("collections").addPathSegment(collection).addPathSegment(uuid.toString()).build();
        LOG.debug("Deleting Resto catalogue entry {} from collection: {}", uuid, collection);

        Request request = new Request.Builder().url(url).delete().build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {;
                LOG.info("Deleted Resto object with ID {}", uuid);
            } else {
                LOG.error("Failed to delete Resto object from collection '{}': {} {}: {}", collection, response.code(), response.message(), response.body());
                throw new CatalogueException("Failed to delete Resto object");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getRestoCollections() {
        HttpUrl allCollectionsUrl = HttpUrl.parse(restoBaseUrl).newBuilder().addPathSegment("collections.json").build();
        Request request = new Request.Builder().url(allCollectionsUrl).build();

        try (Response response = client.newCall(request).execute()) {
            return JsonPath.read(response.body().string(), "$.collections[*].name");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void ensureCollectionExists(String collectionName) {
        if (!getRestoCollections().contains(collectionName)) {
            LOG.debug("Collection '{}' not found, creating", collectionName);
            try (Response response = client
                    .newCall(new Request.Builder()
                            .url(HttpUrl.parse(restoBaseUrl).newBuilder().addPathSegment("collections").build())
                            .post(RequestBody.create(MediaType.parse(APPLICATION_JSON_VALUE), jsonMapper.writeValueAsString(buildCollectionConfig(collectionName))))
                            .build())
                    .execute()) {
                if (response.isSuccessful()) {
                    LOG.info("Created Resto collection '{}'", collectionName);
                } else {
                    LOG.warn("Failed to create Resto collection '{}': {} {}: {}", collectionName, response.code(), response.message(), response.body());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private RestoCollection buildCollectionConfig(String collectionName) {
        RestoCollection.RestoCollectionBuilder builder = RestoCollection.builder()
                .name(collectionName)
                .status("public")
                .licenseId("unlicensed")
                .rights(ImmutableMap.of("download", 0, "visualize", 1));

        // TODO Add WMS mapping properties where possible

        if (collectionName.equals(outputProductCollection)) {
            builder
                    .model(outputProductModel)
                    .osDescription(ImmutableMap.of("en", RestoCollection.OpensearchDescription.builder()
                            .shortName("fstepOutputs")
                            .longName("FS-TEP Output Products")
                            .description("Products created by FS-TEP services")
                            .tags("fstep fs-tep output outputs generated")
                            .query("fstepOutputs")
                            .build()))
                    .propertiesMapping(ImmutableMap.of(
                            "fstepFileType", FstepFile.Type.OUTPUT_PRODUCT.toString())
                    );
        } else if (collectionName.equals(refDataCollection)) {
            builder
                    .model(refDataModel)
                    .osDescription(ImmutableMap.of("en", RestoCollection.OpensearchDescription.builder()
                            .shortName("fstepRefData")
                            .longName("FS-TEP Reference Data Products")
                            .description("Reference data uploaded by FS-TEP users")
                            .tags("fstep fs-tep refData reference")
                            .query("fstepRefData")
                            .build()))
                    .propertiesMapping(ImmutableMap.of(
                            "fstepFileType", FstepFile.Type.REFERENCE_DATA.toString())
                    );
        } else {
            builder
                    .model(externalProductModel)
                    .osDescription(ImmutableMap.of("en", RestoCollection.OpensearchDescription.builder()
                            .shortName(collectionName)
                            .longName("FS-TEP External Products: " + collectionName)
                            .description("External products used as inputs by FS-TEP services (" + collectionName + ")")
                            .tags("fstep fs-tep inputs input external " + collectionName)
                            .query("fstepInputs_" + collectionName)
                            .build()))
                    .propertiesMapping(ImmutableMap.of(
                            "fstepFileType", FstepFile.Type.EXTERNAL_PRODUCT.toString())
                    );
        }

        return builder.build();
    }

}
