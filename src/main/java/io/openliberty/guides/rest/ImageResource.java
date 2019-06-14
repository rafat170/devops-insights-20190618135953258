package io.openliberty.guides.rest;

import java.io.IOException;
import java.io.InputStream;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

@Path("images")
public class ImageResource
{
   // json object keys
   private static final String DOC_KEY_IMAGE = "image";
   private static final String DOC_KEY_IMAGE_TYPE = "imageType";
   private static final String DOC_KEY_LATITUDE = "lat";
   private static final String DOC_KEY_LONGITUDE = "lng";
   private static final String DOC_KEY_TIMESTAMP = "timestamp";

   // query params
   private static final String QUERY_PARAM_DEFAULT_VALUE = "-1";

   // database params
   private static final String MONGODB_URL = "mongodb://localhost:32768";
   private static final String MONGODB_DATABASE_NAME = "freyr";
   private static final String MONGODB_COLLECTION_IMAGE = "images";

   @GET
   @Produces(MediaType.APPLICATION_JSON)
   public Response getImages(
      @DefaultValue("true") @QueryParam("includeImage") boolean includeImage,
      @DefaultValue(QUERY_PARAM_DEFAULT_VALUE) @QueryParam("bounds") String bounds,
      @DefaultValue(QUERY_PARAM_DEFAULT_VALUE) @QueryParam("radius") double radius) throws IOException
   {
      // radius in meters
      JsonArrayBuilder results = Json.createArrayBuilder();
      boolean filterByBounds = !bounds.equals(QUERY_PARAM_DEFAULT_VALUE);
      try (MongoClient mongoClient = MongoClients.create(MONGODB_URL))
      {
         MongoDatabase database = mongoClient.getDatabase(MONGODB_DATABASE_NAME);
         MongoCollection<Document> collection = database.getCollection(MONGODB_COLLECTION_IMAGE);
         MongoCursor<Document> cursor = collection.find().iterator();
         while (cursor.hasNext())
         {
            Document doc = cursor.next();
            if (!includeImage)
            {
               doc.remove(DOC_KEY_IMAGE);
               doc.remove(DOC_KEY_IMAGE_TYPE);
            }
            if (filterByBounds && !isBounded(
               doc.getDouble(DOC_KEY_LATITUDE),
               doc.getDouble(DOC_KEY_LONGITUDE),
               bounds,
               radius))
            {
               System.out.println("ignore");
               continue;
            }
            // FIXME: create POJO
            results.add(doc.toJson());
         }
      }
      return Response.status(Status.OK).entity(results.build()).build();
   }

   @POST
   @Consumes({ "image/jpeg", "image/png" })
   public Response uploadImage(
      InputStream in,
      @HeaderParam("Content-Type") String fileType,
      @DefaultValue(QUERY_PARAM_DEFAULT_VALUE) @QueryParam(DOC_KEY_LATITUDE) double lat,
      @DefaultValue(QUERY_PARAM_DEFAULT_VALUE) @QueryParam(DOC_KEY_LONGITUDE) double lng,
      @DefaultValue(QUERY_PARAM_DEFAULT_VALUE) @QueryParam("origin") String origin,
      @QueryParam(DOC_KEY_TIMESTAMP) long timestamp) throws IOException
   {
      // mongodb has 16 MB document size limit
      byte[] bytes = IOUtils.toByteArray(in);
      if (bytes.length > 16 * 1024 * 1024) // 16 MB 
      {
         JsonObjectBuilder errorMsg = Json.createObjectBuilder();
         errorMsg.add("error", "Image size too large.");
         return Response.status(Status.BAD_REQUEST).entity(errorMsg).build();

      }
      try (MongoClient mongoClient = MongoClients.create(MONGODB_URL))
      {
         MongoDatabase database = mongoClient.getDatabase(MONGODB_DATABASE_NAME);
         MongoCollection<Document> imageCollection = database.getCollection(MONGODB_COLLECTION_IMAGE);
         Document doc = new Document(DOC_KEY_IMAGE, bytes).append(DOC_KEY_TIMESTAMP, timestamp);
         if (!origin.equals(QUERY_PARAM_DEFAULT_VALUE))
         {
            String oLatLng[] = origin.split(",");
            doc.append(DOC_KEY_LATITUDE, Double.parseDouble(oLatLng[0]));
            doc.append(DOC_KEY_LONGITUDE, Double.parseDouble(oLatLng[1]));
         }
         else
         {
            doc.append(DOC_KEY_LATITUDE, lat).append(DOC_KEY_LONGITUDE, lng);
         }
         if (fileType.equals("image/jpeg"))
         {
            doc.append(DOC_KEY_IMAGE_TYPE, "JPEG");
         }
         else
         {
            doc.append(DOC_KEY_IMAGE_TYPE, "PNG");
         }
         imageCollection.insertOne(doc);
         return Response.status(Status.CREATED).build();
      }
   }

   /**
    * north: north latitude of bounding box.
    * west: left longitude of bounding box (western bound). 
    * south: south latitude of the bounding box.
    * east: right longitude of bounding box (eastern bound).
    * latitude: latitude of the point to check.
    * longitude: longitude of the point to check.
    * 
    * https://stackoverflow.com/questions/8542255/how-to-test-if-a-latitude-longitude-point-is-within-a-map-not-google-maps/8542272#8542272
    *       
    */
   private boolean isBounded(
      double north,
      double south,
      double east,
      double west,
      double latitude,
      double longitude)
   {
      /* Check latitude bounds first. */
      if (north >= latitude && latitude >= south)
      {
         /* If your bounding box doesn't wrap 
         the date line the value
         must be between the bounds.
         If your bounding box does wrap the 
         date line it only needs to be  
         higher than the left bound or 
         lower than the right bound. */
         if (west <= east && west <= longitude && longitude <= east)
         {
            return true;
         }
         else if (west > east && (west <= longitude || longitude <= east))
         {
            return true;
         }
      }
      return false;
   }

   /**
    * Calculate distance between two points in latitude and longitude taking
    * into account height difference. If you are not interested in height
    * difference pass 0.0. Uses Haversine method as its base.
    * 
    * lat1, lon1 Start point lat2, lon2 End point el1 Start altitude in meters
    * el2 End altitude in meters
    * 
    * https://stackoverflow.com/questions/3694380/calculating-distance-between-two-points-using-latitude-longitude
    * @returns Distance in Meters
    */
   private double distance(double lat1, double lat2, double lon1, double lon2, double el1, double el2)
   {
      final int R = 6371; // Radius of the earth

      double latDistance = Math.toRadians(lat2 - lat1);
      double lonDistance = Math.toRadians(lon2 - lon1);
      double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(Math.toRadians(lat1))
         * Math.cos(Math.toRadians(lat2)) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
      double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      double distance = R * c * 1000; // convert to meters

      double height = el1 - el2;

      distance = Math.pow(distance, 2) + Math.pow(height, 2);

      return Math.sqrt(distance);
   }

   private boolean isBounded(Double lat, Double lng, String bounds, double radius)
   {
      System.out.println("lat:" + lat + ", lng:" + lng + ", bounds:" + bounds + ", radius:" + radius);
      if (radius != Double.parseDouble(QUERY_PARAM_DEFAULT_VALUE))
      {
         String[] midPoint = bounds.split(",");
         double midLat = Double.parseDouble(midPoint[0]);
         double midLng = Double.parseDouble(midPoint[1]);
         double distance = distance(midLat, lat, midLng, lng, 0, 0);
         if (distance <= radius)
         {
            return true;
         }
      }
      else if (!bounds.equals(QUERY_PARAM_DEFAULT_VALUE))
      {
         String[] boundsArray = bounds.split("|"); // northeast, southwest
         String[] northEast = boundsArray[0].split(",");
         String[] southWest = boundsArray[1].split(",");
         if (isBounded(
            Double.parseDouble(northEast[0]),
            Double.parseDouble(southWest[0]),
            Double.parseDouble(northEast[1]),
            Double.parseDouble(southWest[1]),
            lat,
            lng))
         {
            return true;
         }
      }
      return false;
   }
}
