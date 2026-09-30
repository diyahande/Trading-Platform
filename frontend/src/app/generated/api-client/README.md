# API Client - Auto-Generated TypeScript Angular Client

This directory contains auto-generated TypeScript/Angular API client code that is generated from the trading platform's OpenAPI specification. The code is automatically generated and should not be manually edited.

## Overview

The API client provides type-safe, auto-generated Angular services and models for interacting with the Trade API backend. This ensures consistency between the frontend and backend API contracts.

## Prerequisites

- **Node.js** and npm installed
- **Trade API** service running and accessible at `http://localhost:8081`
- OpenAPI v3 specification available at `/v3/api-docs` endpoint

## Setup Instructions

### 1. Install OpenAPI Generator CLI

Install the OpenAPI Generator CLI as a development dependency:

```bash
npm install @openapitools/openapi-generator-cli --save-dev
```

### 2. Generate API Client

Run the generator to create TypeScript Angular client code from the OpenAPI specification:

```bash
npx openapi-generator-cli generate \
  -i http://localhost:8081/v3/api-docs \
  -g typescript-angular \
  -o src/app/generated/api-client
```

**Parameters:**
- `-i`: Input OpenAPI specification URL (Trade API's Swagger documentation)
- `-g`: Generator type (typescript-angular)
- `-o`: Output directory for generated code

## Backend Configuration

To expose the OpenAPI documentation endpoint, configure the following in your Spring Boot backend:

### WebSecurityCustomizer Configuration

Add this bean to allow public access to Swagger/OpenAPI documentation:

```java
@Bean
public WebSecurityCustomizer webSecurityCustomizer() {
    return (web) -> web.ignoring().requestMatchers(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );
}
```

This configuration:
- Exposes `/v3/api-docs/**` - OpenAPI specification endpoints
- Exposes `/swagger-ui/**` - Swagger UI static resources
- Exposes `/swagger-ui.html` - Swagger UI home page

## Generated Code Structure

The generated code includes:

- **Models**: TypeScript interfaces and classes representing API request/response data
- **Services**: Angular services with methods for each API endpoint
- **API Configuration**: Default API configuration and HTTP client setup

## Using the Generated Client

Import and inject the generated services into your Angular components:

```typescript
import { OrderService } from './generated/api-client';

export class OrderComponent {
  constructor(private orderService: OrderService) {}

  getOrders() {
    this.orderService.getOrders().subscribe(
      (orders) => console.log(orders),
      (error) => console.error(error)
    );
  }
}
```

## Regeneration

Whenever the backend API changes:

1. Ensure the Trade API is running with updated OpenAPI documentation
2. Run the generator command again
3. Review changes in version control
4. Update any consuming code as needed

## Important Notes

- **Do not manually edit** files in this directory - they will be overwritten during regeneration
- Keep the Trade API service running during code generation
- Ensure the OpenAPI specification URL is accessible from your development machine
- Generated code should be committed to version control for consistency across the team

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Connection refused to `localhost:8081` | Ensure Trade API service is running |
| Missing `/v3/api-docs` endpoint | Verify Spring Boot Springdoc setup in Trade API |
| Type errors after generation | Clear node_modules and reinstall dependencies |
| CORS issues | Check backend CORS configuration |