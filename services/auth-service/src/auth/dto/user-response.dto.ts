import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class UserResponseDto {
  @ApiProperty({ format: 'uuid' })
  id!: string;

  @ApiProperty()
  username!: string;

  @ApiProperty({ format: 'uuid' })
  accountId!: string;

  @ApiProperty({ type: [String], enum: ['CUSTOMER', 'ADMIN'] })
  roles!: string[];

  @ApiPropertyOptional()
  firstName?: string;

  @ApiPropertyOptional()
  lastName?: string;

  @ApiPropertyOptional({ format: 'date-time' })
  createdOn?: string;
}
