import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  ArrayMinSize,
  IsArray,
  IsEmail,
  IsOptional,
  IsString,
  IsUUID,
  Length,
  Matches,
} from 'class-validator';

import type { Role } from '../types/auth.types';

export class RegisterRequestDto {
  @ApiProperty({
    example: 'vijay@example.com',
  })
  @IsString()
  @IsEmail()
  username!: string;

  @ApiProperty({ minLength: 12, maxLength: 128 })
  @IsString()
  @Length(12, 128)
  password!: string;

  @ApiProperty({ format: 'uuid' })
  @IsUUID('4')
  accountId!: string;

  @ApiPropertyOptional({ type: [String], enum: ['CUSTOMER', 'ADMIN'] })
  @IsOptional()
  @IsArray()
  @ArrayMinSize(1)
  @IsString({ each: true })
  roles?: Role[];
}
