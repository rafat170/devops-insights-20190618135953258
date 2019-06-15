### Deploy application (locally)
- Prereq: Docker, Maven, cURL
- Clone this repository using git clone command
- At root project directory level, run `mvn install liberty:run-server`
- Test server, run `curl -X GET http://localhost:9080/LibertyProject/System/properties`
- To stop server, enter `Ctrl+C`

#### Run MongoDB
- `docker run -p 32768:27017 --name some-mongo -d mongo:latest`

#### Run MongoDB Dashboard (optional)
- `docker run -it --rm -p 8081:8081 --link some-mongo:mongo mongo-express`

---

### API

#### POST /images

Required headers:
- Content-Type -- Supported types: `image/jpeg` or `image/png`

Required parameters: 
- timestamp -- Currently just a long number, eg. `18923819`
- origin -- Latitude and longitude values without any spaces, eg. `origin=34.34341,-90.12389`
- lat -- Latitude value (required, if not using `origin`)
- lng -- Longitude value (required, if not using `origin`)

POST /LibertyProject/System/images?timestamp=12345&origin=43.819612,-79.324298

curl -X POST   'http://localhost:9080/LibertyProject/System/images?timestamp=12345&origin=43.819612,-79.324298'   -H 'Content-Type: image/jpeg'   --data-binary @/Users/wasifk/Desktop/flood.jpg

#### GET /images

Required parameters: None

Optional parameters:
- includeImage -- Set to `false` to not include image binary data in response.
- bounds -- Either a single latitude and longitude value pair, or double. Pairs are separated by `|`. Single pair expected when using `radius` parameter. If `radius` parameter is not provided, bounds is expected to have 2 pairs to create a rectangular bounded region. Example: `bounds=43.11234,88.45334` (single pair), `bounds=43.55645,-90.45321|38.12389,-80.33212` (double pair)
- radius -- In meters. Radius combined with bounds (single pair) determine a bounded region for images returned.

GET /LibertyProject/System/images?includeImage=false&bounds=43.819612,-79.324298&radius=100000

curl -X GET \
  'http://localhost:9080/LibertyProject/System/images?includeImage=false&bounds=43.819612,-79.324298&radius=100000'

