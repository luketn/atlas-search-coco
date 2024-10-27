mongoimport --db AtlasSearchCoco --collection Category --type json --jsonArray --drop --file ./data/category.json
mongoimport --db AtlasSearchCoco --collection License --type json --jsonArray --drop --file ./data/license.json
mongoimport --db AtlasSearchCoco --collection Image --type json --jsonArray --drop --file ./data/image.json

# Read the JSON file and escape quotes for mongo shell
JSON_CONTENT=$(cat src/main/resources/atlas-search-index.json)

# Create a temporary JavaScript file
TMP_JS=$(mktemp)
cat << EOF > "$TMP_JS"
const JSON_IMPORTED_DEFINITION = $JSON_CONTENT;
EOF

# Run the JavaScript file with mongosh
mongosh --file $TMP_JS --file create-index.js

# Clean up
rm "$TMP_JS"