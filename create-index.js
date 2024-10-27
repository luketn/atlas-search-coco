const indexDefinition = JSON_IMPORTED_DEFINITION;

let collection = db.getSiblingDB('AtlasSearchCoco').getCollection('Image');
let indexName = 'default';

function getAtlasSearchIndex() {
    let currentIndexes = collection.getSearchIndexes(indexName);
    if (currentIndexes.length > 0) {
        return currentIndexes[0];
    } else {
        return null;
    }
}

function dropAtlasSearchIndex() {
    collection.dropSearchIndex(indexName)
}

function waitStatusReady() {
    let currentIndex = getAtlasSearchIndex();
    while (currentIndex != null && currentIndex.status !== 'READY') {
        sleep(1000);
        currentIndex = getAtlasSearchIndex();
    }
}

function waitNoIndex() {
    let currentIndex = getAtlasSearchIndex();
    while (currentIndex != null) {
        sleep(1000);
        currentIndex = getAtlasSearchIndex();
    }
}


let hasCurrentIndex = getAtlasSearchIndex() != null;
if (hasCurrentIndex) {
    console.log(`Dropping existing atlasSearch index...`);
    dropAtlasSearchIndex();
    waitNoIndex();
}

let mappingFieldNames = Object.keys(indexDefinition.mappings.fields).sort();
console.log(`Creating atlasSearch index with ${mappingFieldNames.length} fields: [${mappingFieldNames.join(', ')}].`);
collection.createSearchIndex(indexName, indexDefinition);
waitStatusReady();

let createdIndex = getAtlasSearchIndex();
let createdFields = Object.keys(createdIndex.latestDefinition.mappings.fields).sort();
console.log(`Created atlasSearch index with ${createdFields.length} fields:  [${createdFields.join(', ')}], status: ${createdIndex.status}.`);
