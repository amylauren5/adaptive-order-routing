const Migrations = artifacts.require("Migrations");
const FoodCart = artifacts.require("FoodCartContract");
const Product = artifacts.require("MedicineStorage.sol");

module.exports = function (deployer) {
    deployer.deploy(Migrations);
    deployer.deploy(FoodCart);
    deployer.deploy(Product);
};