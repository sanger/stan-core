# Stan (Core)

![CI](https://github.com/sanger/stan-core/workflows/CI/badge.svg)

### TODO ###

* [ ] Finish README


  Database setup:

* Install MySQL  via home-brew
* Create a schema in your local mysql db called stan
* Create users and passwords in your local mysql matching the details in `application-dev.properties`
* Create appropriate permissions on the user
* In your run configuration for StanApplication, override the parameter `spring.profiles.active` giving it the value `dev`.


    For setting users
    
        $ mysql -u root
        > create user 'stan'@'%' identified by 'stanpassword';
        > grant delete, insert, execute, select, update on `stan%`.* to 'stan'@'%';
        > create user 'stan_admin'@'%' identified by 'stanadminpassword';
        > grant all on `stan%`.* to 'stan_admin'@'%';



    Use the schema and static data patches in the `stan-sql` repo to set up your schema 

    ```
    > Create a db schema using file stan-sql/schema/create_schema.sql
    > Run script ./cat_sequence.py schema static view which will output SQL to set up database (There is a file called sequence.txt in stan-sql/sequence.txt that list all the patches applied
    > Create an empty schema called `stantest` for the tests to run in. Liquibase is responsible for setting up tables and data for unit tests.
    ````

Notes:-
 * For your unit test schema, liquibase should update it automatically when you run unit tests. If it doesn't, run liquibase.dropAll in the  tab of IntelliJ
 * For your schema that you use to run core locally, all the required changes are defined in the stan-sql repo and listed in sequence.txt. The cat_sequence.py script in that repo will concatenate patches together in a particular range for you to run into your database, and it will add records to the db_history table of your schema so you can check which patches you have already applied.


Stan Application:
  * Install IntelliJ
  * Load stan-core application in IntelliJ
  * Update stan.mail.alert.recipents field in application-dev.properties so it won’t send email in case of an error in your local setup.
  * Run the package job in the maven tab of IntelliJ
  * To run the application, find and run the StanApplication class


