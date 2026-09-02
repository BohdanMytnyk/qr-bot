const databaseName = process.env.MONGO_APP_DATABASE || "qr_bot";
const username = process.env.MONGO_APP_USERNAME;
const password = process.env.MONGO_APP_PASSWORD;

if (!username || !password) {
    throw new Error("MONGO_APP_USERNAME and MONGO_APP_PASSWORD are required");
}

db = db.getSiblingDB(databaseName);
db.createUser({
    user: username,
    pwd: password,
    roles: [{role: "readWrite", db: databaseName}]
});
