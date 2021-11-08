# Stan (Core)

![CI](https://github.com/sanger/stan-core/workflows/CI/badge.svg)

### TODO ###

* [ ] Finish README

Database setup:

* Create a schema in your local mysql db called stan
* Create users and passwords in your local mysql matching the details in `application-dev.properties`
* Create appropriate permissions on the user
* In your run configuration for StanApplication, override the parameter `spring.profiles.active` giving it the value `dev`.

```
$ mysql -u root
> create user 'stan'@'%' identified by 'stanpassword';
> grant delete, insert, execute, select, update on `stan%`.* to 'stan'@'%';
> create user 'stan_admin'@'%' identified by 'stanadminpassword';
> grant all on `stan%`.* to 'stan_admin'@'%';
```

Use the schema and static data patches in the `stan-sql` repo to set up your schema, starting with
`schema/create_schema.sql`.
You might need to create an empty schema called `stantest` for the tests to run in. Liquibase is
responsible for setting up tables and data for unit tests.
