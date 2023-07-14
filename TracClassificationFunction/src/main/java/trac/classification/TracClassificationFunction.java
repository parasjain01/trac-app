package trac.classification;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.S3Object;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.DetectCustomLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectCustomLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.CustomLabel;
import software.amazon.awssdk.services.rekognition.model.RekognitionException;

import java.util.List;

import java.util.logging.Logger;


/**
 * Handler for requests to Lambda function.
 */
public class TracClassificationFunction implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    public static final Logger logger = Logger.getLogger(TracClassificationFunction.class.getName());
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        String output = "";
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        try {
            String arn = System.getenv("TRAC_REKOGNITION_ARN");
            String bucket_name = null;
            String image_key = null;
            if(input.getQueryStringParameters() != null &&
                    input.getQueryStringParameters().containsKey("bucket_name")){
                bucket_name = input.getQueryStringParameters().get("bucket_name");
            }
            if(input.getQueryStringParameters() != null &&
                    input.getQueryStringParameters().containsKey("image_name")){
                image_key = input.getQueryStringParameters().get("image_name");
            }
            output += "\nARN is " + arn;
            output += "\nBucket is " + bucket_name;
            output += "\nImage is " + image_key;
            final String USAGE = "\n" +
                    "Usage: " +
                    "DetectLabels <project arn> <S3 bucket> <S3 key>\n\n" +
                    "Where:\n" +
                    "project arn - the arn of the model in Rekognition Custom Labels to the image (for example, arn:aws:rekognition:us-east-1:XXXXXXXXXXXX:project/YOURPROJECT/version/YOURPROJECT.YYYY-MM-DDT00.00.00/1234567890123). \n" +
                    "S3 bucket - the bucket where your image is stored (for example, my-bucket-name \n" +
                    "S3 key - the path of the image inside your bucket (for example, myfolder/pic1.png). \n\n";

            if (arn == null || bucket_name == null || image_key== null ) {
                output += USAGE;
                return response
                        .withStatusCode(200)
                        .withBody(output);
            }

            Region region = Region.US_EAST_1;
            RekognitionClient rekClient = RekognitionClient.builder()
                    .region(region)
                    .build();

            Map<String, String> customLabels = detectImageCustomLabels(rekClient, arn, bucket_name, image_key );
            rekClient.close();

            if(customLabels.size()>0){
                for (var entry : customLabels.entrySet()) {
                    output += "\nLabelName: " + entry.getKey() + ", LabelValue: " + entry.getValue();
                }

            }else {
                output += "\nNo Custom label found";
            }
            return response
                    .withStatusCode(200)
                    .withBody(output);
        } catch (Exception e) {
            return response
                    .withBody("{}")
                    .withStatusCode(500);
        }
    }

    public static Map<String, String> detectImageCustomLabels(RekognitionClient rekClient, String arn, String bucket, String key ) {
        Map<String,String> customLabelMap = new HashMap<String,String>();
        try {

            S3Object s3Object = S3Object.builder()
                    .bucket(bucket)
                    .name(key)
                    .build();

            // Create an Image object for the source image
            Image s3Image = Image.builder()
                    .s3Object(s3Object)
                    .build();

            DetectCustomLabelsRequest detectCustomLabelsRequest = DetectCustomLabelsRequest.builder()
                    .image(s3Image)
                    .projectVersionArn(arn)
                    .build();

            DetectCustomLabelsResponse customLabelsResponse = rekClient.detectCustomLabels(detectCustomLabelsRequest);
            List<CustomLabel> customLabels = customLabelsResponse.customLabels();

            for (CustomLabel customLabel: customLabels) {
                System.out.println(customLabel.name() + ": " + customLabel.confidence().toString());
                customLabelMap.put(customLabel.name(), customLabel.confidence().toString());
            }
        } catch (RekognitionException e) {
            System.exit(1);
        }
        return customLabelMap;
    }
}
