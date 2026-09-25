import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  ArrayMinSize,
  IsArray,
  IsOptional,
  IsString,
  IsUUID,
  Length,
  Matches,
} from 'class-validator';

import type { Role } from '../types/auth.types';

export class RegisterRequestDto {
  @ApiProperty({ minLength: 3, maxLength: 64 })
  @IsString()
  @Length(3, 64)
  @Matches(/^[a-zA-Z0-9._-]+$/)
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
