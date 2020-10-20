# Stan (Core)

![CI](https://github.com/sanger/stan-core/workflows/CI/badge.svg)

### TODO ###

* [ ] Finish README

Database setup:

* Create a schema in your local mysql db called stan
* Create a user and password in your local mysql matching the details in `application-dev.properties`
* Create appropriate permissions on the user
* Execute the sql files to set up the schema
* In your run configuration for StanApplication, override the parameter `spring.profiles.active` giving it the value `dev`.

```
$ mysql -u root
> create schema stan;
> create user 'stan'@'%' identified by 'stanpassword';
> grant delete, insert, execute, select, update on stan.* to 'stan'@'%';
```